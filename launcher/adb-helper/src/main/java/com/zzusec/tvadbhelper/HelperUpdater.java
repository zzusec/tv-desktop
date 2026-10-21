package com.zzusec.tvadbhelper;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

final class HelperUpdater {
    private static final String METADATA_URL = "https://raw.githubusercontent.com/zzusec/"
            + "tv-desktop/main/launcher/adb-helper/update.json";
    private final Context context;

    static final class Release {
        final int versionCode;
        final String versionName;
        final String apkUrl;
        final String sha256;

        Release(int versionCode, String versionName, String apkUrl, String sha256) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
        }
    }

    HelperUpdater(Context context) {
        this.context = context.getApplicationContext();
    }

    Release check() throws IOException {
        requireWorker();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        fetch(METADATA_URL, UpdatePolicy.METADATA_LIMIT, bytes);
        try {
            JSONObject json = new JSONObject(new String(bytes.toByteArray(), Charset.forName("UTF-8")));
            Object version = json.get("versionCode");
            if (!(version instanceof Integer)) throw new IOException("Invalid metadata versionCode");
            Release release = new Release((Integer) version, json.getString("versionName"),
                    json.getString("apkUrl"), json.getString("sha256"));
            UpdatePolicy.validateRelease(release.versionCode, release.versionName, release.apkUrl, release.sha256);
            return release.versionCode > installed().versionCode ? release : null;
        } catch (JSONException error) {
            throw new IOException("Invalid update metadata", error);
        }
    }

    synchronized File download(Release release) throws IOException {
        requireWorker();
        UpdatePolicy.validateRelease(release.versionCode, release.versionName, release.apkUrl, release.sha256);
        if (release.versionCode <= installed().versionCode) throw new IOException("Update is not newer");
        File directory = new File(context.getCacheDir(), "update");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create update cache");
        File partial = new File(directory, "helper-update.apk.part");
        File target = new File(directory, "helper-update.apk");
        try {
            try (FileOutputStream output = new FileOutputStream(partial)) {
                fetch(release.apkUrl, UpdatePolicy.APK_LIMIT, output);
                output.getFD().sync();
            }
            validateApk(partial, release);
            if (target.exists() && !target.delete()) throw new IOException("Cannot replace cached APK");
            if (!partial.renameTo(target)) throw new IOException("Cannot finalize downloaded APK");
            return target;
        } finally {
            if (partial.exists() && !partial.delete()) throw new IOException("Cannot remove partial update APK");
        }
    }

    synchronized File prepareInstall(Release release) throws IOException {
        requireWorker();
        UpdatePolicy.validateRelease(release.versionCode, release.versionName, release.apkUrl, release.sha256);
        File cached = new File(context.getCacheDir(), "update/helper-update.apk");
        validateApk(cached, release);
        if (Build.VERSION.SDK_INT >= 21) return cached;

        // KitKat's installer accepts file: only, and cannot traverse our private cache.
        File external = context.getExternalFilesDir(null);
        if (external == null) throw new IOException("External storage unavailable for legacy installer");
        File directory = new File(external, "updates");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create legacy installer directory");
        }
        File partial = new File(directory, "helper-update.apk.part");
        File target = new File(directory, "helper-update.apk");
        try {
            try (InputStream input = new java.io.FileInputStream(cached);
                 FileOutputStream output = new FileOutputStream(partial)) {
                byte[] buffer = new byte[8192];
                int count;
                int total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    UpdatePolicy.validateLength(total, UpdatePolicy.APK_LIMIT);
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            validateApk(partial, release);
            if (target.exists() && !target.delete()) throw new IOException("Cannot replace legacy installer APK");
            if (!partial.renameTo(target)) throw new IOException("Cannot finalize legacy installer APK");
            return target;
        } finally {
            if (partial.exists() && !partial.delete()) throw new IOException("Cannot remove partial legacy APK");
        }
    }

    private void validateApk(File file, Release release) throws IOException {
        if (!file.isFile()) throw new IOException("Update APK is missing");
        UpdatePolicy.validateLength(file.length(), UpdatePolicy.APK_LIMIT);
        if (!release.sha256.equalsIgnoreCase(sha256(file))) throw new IOException("APK SHA-256 mismatch");
        PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(
                file.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        if (archive == null) throw new IOException("Cannot parse downloaded APK");
        PackageInfo local = installed();
        UpdatePolicy.validateArchive(archive.packageName, archive.versionCode, release.versionCode,
                local.versionCode, signatures(local.signatures), signatures(archive.signatures));
    }

    private PackageInfo installed() throws IOException {
        try {
            return context.getPackageManager().getPackageInfo(UpdatePolicy.PACKAGE_NAME,
                    PackageManager.GET_SIGNATURES);
        } catch (PackageManager.NameNotFoundException error) {
            throw new IOException("Cannot inspect installed helper", error);
        }
    }

    private static byte[][] signatures(Signature[] signatures) {
        if (signatures == null) return null;
        byte[][] bytes = new byte[signatures.length][];
        for (int i = 0; i < signatures.length; i++) bytes[i] = signatures[i].toByteArray();
        return bytes;
    }

    private static void requireWorker() throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IOException("Update must run on a worker thread");
    }

    private void fetch(String address, int limit, OutputStream output) throws IOException {
        URL url = UpdatePolicy.trustedUrl(address);
        long deadline = System.nanoTime() + 120_000_000_000L;
        for (int redirects = 0; redirects <= 5; redirects++) {
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(15_000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("User-Agent", "TV-ADB-Helper");
            if (Build.VERSION.SDK_INT <= 23) {
                connection.setSSLSocketFactory(new LegacyTlsFactory(context));
            }
            try {
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Update redirect has no Location");
                    url = UpdatePolicy.trustedUrl(new URL(url, location).toExternalForm());
                    continue;
                }
                if (status != 200) throw new IOException("Update HTTP status " + status);
                String encoding = connection.getContentEncoding();
                if (encoding != null && !"identity".equalsIgnoreCase(encoding)) {
                    throw new IOException("Unsupported update content encoding");
                }
                UpdatePolicy.validateLength(connection.getContentLength(), limit);
                try (InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (System.nanoTime() > deadline) throw new IOException("Update transfer timed out");
                        total += count;
                        UpdatePolicy.validateLength(total, limit);
                        output.write(buffer, 0, count);
                    }
                    if (total == 0) throw new IOException("Empty update response");
                }
                return;
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many update redirects");
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                int total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    UpdatePolicy.validateLength(total, UpdatePolicy.APK_LIMIT);
                    digest.update(buffer, 0, count);
                }
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest.digest()) hex.append(String.format(Locale.US, "%02x", value & 0xff));
            return hex.toString();
        } catch (GeneralSecurityException error) {
            throw new IOException("SHA-256 unavailable", error);
        }
    }

    // Used only for the fixed HTTPS host allowlist, without changing hostname verification.
    private static final class LegacyTlsFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        LegacyTlsFactory(Context context) throws IOException {
            try {
                X509TrustManager system = trustManager(null);
                KeyStore roots = KeyStore.getInstance(KeyStore.getDefaultType());
                roots.load(null, null);
                CertificateFactory certificates = CertificateFactory.getInstance("X.509");
                int[] resources = {R.raw.isrg_root_x1, R.raw.usertrust_ecc};
                for (int resource : resources) {
                    try (InputStream input = context.getResources().openRawResource(resource)) {
                        X509Certificate certificate = (X509Certificate) certificates.generateCertificate(input);
                        if (certificate.getBasicConstraints() < 0) {
                            throw new CertificateException("Bundled update certificate is not a CA");
                        }
                        certificate.verify(certificate.getPublicKey());
                        roots.setCertificateEntry(Integer.toString(resource), certificate);
                    }
                }
                X509TrustManager bundled = trustManager(roots);
                X509TrustManager combined = new X509TrustManager() {
                    @Override public void checkClientTrusted(X509Certificate[] chain, String authType)
                            throws CertificateException {
                        system.checkClientTrusted(chain, authType);
                    }

                    @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                            throws CertificateException {
                        try {
                            system.checkServerTrusted(chain, authType);
                        } catch (CertificateException systemError) {
                            try {
                                bundled.checkServerTrusted(chain, authType);
                            } catch (CertificateException bundledError) {
                                bundledError.addSuppressed(systemError);
                                throw bundledError;
                            }
                        }
                    }

                    @Override public X509Certificate[] getAcceptedIssuers() {
                        X509Certificate[] first = system.getAcceptedIssuers();
                        X509Certificate[] second = bundled.getAcceptedIssuers();
                        X509Certificate[] all = new X509Certificate[first.length + second.length];
                        System.arraycopy(first, 0, all, 0, first.length);
                        System.arraycopy(second, 0, all, first.length, second.length);
                        return all;
                    }
                };
                SSLContext ssl = SSLContext.getInstance("TLS");
                ssl.init(null, new TrustManager[]{combined}, null);
                delegate = ssl.getSocketFactory();
            } catch (GeneralSecurityException error) {
                throw new IOException("Cannot configure legacy update TLS", error);
            }
        }

        private static X509TrustManager trustManager(KeyStore roots) throws GeneralSecurityException {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(roots);
            for (TrustManager manager : factory.getTrustManagers()) {
                if (manager instanceof X509TrustManager) return (X509TrustManager) manager;
            }
            throw new GeneralSecurityException("No X509 trust manager available");
        }

        private Socket secure(Socket socket) {
            if (Build.VERSION.SDK_INT <= 20) {
                ((SSLSocket) socket).setEnabledProtocols(new String[]{"TLSv1.2"});
            }
            return socket;
        }
        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public Socket createSocket() throws IOException { return secure(delegate.createSocket()); }
        @Override public Socket createSocket(Socket s, String h, int p, boolean close) throws IOException {
            return secure(delegate.createSocket(s, h, p, close));
        }
        @Override public Socket createSocket(String h, int p) throws IOException {
            return secure(delegate.createSocket(h, p));
        }
        @Override public Socket createSocket(String h, int p, InetAddress local, int port) throws IOException {
            return secure(delegate.createSocket(h, p, local, port));
        }
        @Override public Socket createSocket(InetAddress h, int p) throws IOException {
            return secure(delegate.createSocket(h, p));
        }
        @Override public Socket createSocket(InetAddress h, int p, InetAddress local, int port) throws IOException {
            return secure(delegate.createSocket(h, p, local, port));
        }
    }
}
