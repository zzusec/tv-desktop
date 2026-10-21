package com.zzusec.tvadbhelper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;

final class UpdatePolicy {
    static final String PACKAGE_NAME = "com.zzusec.tvadbhelper";
    static final int METADATA_LIMIT = 64 * 1024;
    static final int APK_LIMIT = 10 * 1024 * 1024;

    private UpdatePolicy() { }

    static URL trustedUrl(String value) throws IOException {
        if (value == null || value.length() > 4096) throw new IOException("Invalid update URL length");
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !("raw.githubusercontent.com".equalsIgnoreCase(host)
                    || "github.com".equalsIgnoreCase(host)
                    || "release-assets.githubusercontent.com".equalsIgnoreCase(host)
                    || "objects.githubusercontent.com".equalsIgnoreCase(host))) {
                throw new IOException("Untrusted update URL");
            }
            return uri.toURL();
        } catch (URISyntaxException | IllegalArgumentException error) {
            throw new IOException("Invalid update URL", error);
        }
    }

    static void validateRelease(int versionCode, String name, String url, String hash)
            throws IOException {
        if (versionCode <= 0 || name == null || name.trim().isEmpty() || name.length() > 100) {
            throw new IOException("Invalid release version");
        }
        if (hash == null || !hash.matches("[0-9a-fA-F]{64}")) {
            throw new IOException("Invalid release SHA-256");
        }
        trustedUrl(url);
    }

    static void validateLength(long length, int limit) throws IOException {
        if (length > limit) throw new IOException("Update response exceeds size limit");
    }

    static void validateArchive(String packageName, int archiveVersion, int releaseVersion,
                                int installedVersion, byte[][] installedSignatures,
                                byte[][] archiveSignatures) throws IOException {
        if (!PACKAGE_NAME.equals(packageName)) throw new IOException("APK package name mismatch");
        if (archiveVersion != releaseVersion || archiveVersion <= installedVersion) {
            throw new IOException("APK version mismatch or downgrade");
        }
        if (!sameSignatures(installedSignatures, archiveSignatures)) {
            throw new IOException("APK signing certificate mismatch");
        }
    }

    private static boolean sameSignatures(byte[][] expected, byte[][] actual) {
        if (expected == null || actual == null || expected.length == 0
                || expected.length != actual.length) return false;
        boolean[] matched = new boolean[actual.length];
        for (byte[] signature : expected) {
            if (signature == null || signature.length == 0) return false;
            boolean found = false;
            for (int i = 0; i < actual.length; i++) {
                if (!matched[i] && actual[i] != null && MessageDigest.isEqual(signature, actual[i])) {
                    matched[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}
