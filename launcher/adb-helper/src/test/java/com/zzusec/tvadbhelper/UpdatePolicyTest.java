package com.zzusec.tvadbhelper;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class UpdatePolicyTest {
    @Test public void legacyInstallersRequireFileUrisUntilNougat() {
        for (int api : new int[] {19, 20, 21, 22, 23}) assertTrue(UpdatePolicy.usesFileInstaller(api));
        for (int api : new int[] {24, 26, 34}) assertFalse(UpdatePolicy.usesFileInstaller(api));
    }
    private static final String URL = "https://github.com/zzusec/tv-desktop/releases/download/v1/helper.apk";
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final byte[][] CERTS = {{1, 2, 3}, {4, 5, 6}};

    interface Checked { void run() throws IOException; }

    private void rejected(Checked operation) throws Exception {
        try {
            operation.run();
            fail("Expected IOException");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test public void acceptsOnlyExpectedHttpsHosts() throws Exception {
        for (String host : new String[]{"github.com", "raw.githubusercontent.com",
                "release-assets.githubusercontent.com", "objects.githubusercontent.com"}) {
            assertEquals(host, UpdatePolicy.trustedUrl("https://" + host + "/a").getHost());
        }
        assertEquals(443, UpdatePolicy.trustedUrl("https://github.com:443/a").getPort());
    }

    @Test public void rejectsUntrustedTransportAndAuthority() throws Exception {
        rejected(() -> UpdatePolicy.trustedUrl(null));
        for (String url : new String[]{"http://github.com/a", "https://evil.test/a",
                "https://github.com.evil.test/a", "https://github.com@evil.test/a",
                "https://user:pass@github.com/a", "https://github.com:444/a",
                "https://github.com/a#fragment", "file:///tmp/a", "//github.com/a",
                "https://github.com./a", "https://github.com\\@evil.test/a", "not a url"}) {
            rejected(() -> UpdatePolicy.trustedUrl(url));
        }
    }

    @Test public void validatesReleaseFields() throws Exception {
        UpdatePolicy.validateRelease(2, "1.1", URL, HASH);
        UpdatePolicy.validateRelease(2, "1.1", URL, HASH.toUpperCase(java.util.Locale.US));
        rejected(() -> UpdatePolicy.validateRelease(0, "1.1", URL, HASH));
        rejected(() -> UpdatePolicy.validateRelease(-1, "1.1", URL, HASH));
        rejected(() -> UpdatePolicy.validateRelease(2, " ", URL, HASH));
        rejected(() -> UpdatePolicy.validateRelease(2, null, URL, HASH));
        rejected(() -> UpdatePolicy.validateRelease(2, "1.1", URL, HASH.substring(1)));
        rejected(() -> UpdatePolicy.validateRelease(2, "1.1", URL, HASH + "0"));
        rejected(() -> UpdatePolicy.validateRelease(2, "1.1", URL, HASH.replace('a', 'g')));
        rejected(() -> UpdatePolicy.validateRelease(2, "1.1", URL, null));
    }

    @Test public void enforcesSizeBoundsIncludingChunkedTransfers() throws Exception {
        UpdatePolicy.validateLength(-1, UpdatePolicy.APK_LIMIT);
        UpdatePolicy.validateLength(UpdatePolicy.APK_LIMIT, UpdatePolicy.APK_LIMIT);
        rejected(() -> UpdatePolicy.validateLength((long) UpdatePolicy.APK_LIMIT + 1, UpdatePolicy.APK_LIMIT));
        rejected(() -> UpdatePolicy.validateLength(65537, UpdatePolicy.METADATA_LIMIT));
    }

    @Test public void acceptsMatchingNewerApkWithReorderedCertificates() throws Exception {
        UpdatePolicy.validateArchive(UpdatePolicy.PACKAGE_NAME, 2, 2, 1, CERTS,
                new byte[][]{{4, 5, 6}, {1, 2, 3}});
    }

    @Test public void rejectsWrongPackageAndVersions() throws Exception {
        rejected(() -> UpdatePolicy.validateArchive("com.attacker", 2, 2, 1, CERTS, CERTS));
        rejected(() -> UpdatePolicy.validateArchive(UpdatePolicy.PACKAGE_NAME, 3, 2, 1, CERTS, CERTS));
        rejected(() -> UpdatePolicy.validateArchive(UpdatePolicy.PACKAGE_NAME, 1, 1, 1, CERTS, CERTS));
        rejected(() -> UpdatePolicy.validateArchive(UpdatePolicy.PACKAGE_NAME, 1, 1, 2, CERTS, CERTS));
    }

    @Test public void rejectsMissingDifferentAndDuplicateCertificates() throws Exception {
        for (byte[][] certs : new byte[][][]{null, {}, {{1, 2, 3}}, {{1, 2, 3}, {7}},
                {{1, 2, 3}, {1, 2, 3}}, {null, {4, 5, 6}}}) {
            rejected(() -> UpdatePolicy.validateArchive(UpdatePolicy.PACKAGE_NAME, 2, 2, 1, CERTS, certs));
        }
        rejected(() -> UpdatePolicy.validateArchive(UpdatePolicy.PACKAGE_NAME, 2, 2, 1,
                new byte[][]{{}}, new byte[][]{{}}));
    }
}
