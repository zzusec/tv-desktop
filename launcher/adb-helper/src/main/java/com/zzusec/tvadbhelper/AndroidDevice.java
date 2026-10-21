package com.zzusec.tvadbhelper;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

final class AndroidDevice implements AdbController.Device {
    private final Context context;

    AndroidDevice(Context context) {
        this.context = context.getApplicationContext();
    }

    @SuppressLint("PrivateApi")
    @Override public String property(String key) throws Exception {
        return (String) Class.forName("android.os.SystemProperties")
                .getMethod("get", String.class, String.class).invoke(null, key, "");
    }

    @Override public String adbSetting() {
        return String.valueOf(Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0));
    }

    @Override public void enableAdbSetting() {
        if (context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("WRITE_SECURE_SETTINGS not granted; normal apps cannot self-grant it");
        }
        if (!Settings.Global.putInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 1)) {
            throw new IllegalStateException("Settings provider rejected adb_enabled");
        }
    }

    @SuppressLint("PrivateApi")
    @Override public void setProperty(String key, String value) throws Exception {
        try {
            Class.forName("android.os.SystemProperties").getMethod("set", String.class, String.class)
                    .invoke(null, key, value);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw error;
        }
    }

    @Override public String processes() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("/system/bin/ps").redirectErrorStream(true).start();
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicBoolean timedOut = new AtomicBoolean(false);
        Thread timeout = new Thread(() -> {
            try {
                Thread.sleep(2000);
                if (!finished.get()) {
                    timedOut.set(true);
                    process.destroy();
                }
            } catch (InterruptedException ignored) {
                // Cancelled after the bounded command completed.
            }
        }, "adb-helper-ps-timeout");
        timeout.setDaemon(true);
        timeout.start();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            try (InputStream input = process.getInputStream()) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (output.size() + read > 131072) {
                        throw new IOException("ps output exceeded 128 KiB limit");
                    }
                    output.write(buffer, 0, read);
                }
            }
            int exit = process.waitFor();
            if (timedOut.get()) throw new IOException("ps timed out after 2 seconds");
            if (exit != 0) throw new IOException("ps exited " + exit);
            StringBuilder matches = new StringBuilder();
            for (String line : output.toString("UTF-8").split("\n")) {
                if (line.matches(".*\\badbd\\b.*")) matches.append(line.trim()).append('\n');
            }
            return matches.length() == 0
                    ? "No adbd in the visible process list (not proof that it is absent)"
                    : matches.toString().trim();
        } finally {
            finished.set(true);
            timeout.interrupt();
            process.destroy();
        }
    }

    @Override public AdbProbe.Result probe() {
        return AdbProbe.inspect(5555);
    }

    @Override public Map<String, AdbProbe.Result> lanProbes() throws SocketException {
        Map<String, AdbProbe.Result> results = new LinkedHashMap<>();
        for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            for (InetAddress address : Collections.list(network.getInetAddresses())) {
                if (address instanceof Inet4Address && !address.isLoopbackAddress()
                        && !address.isLinkLocalAddress()) {
                    String host = address.getHostAddress();
                    results.put(network.getName() + " " + host, AdbProbe.inspect(host, 5555));
                }
            }
        }
        return results;
    }

    @Override public void awaitService() throws InterruptedException {
        Thread.sleep(1500);
    }
}
