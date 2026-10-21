package com.zzusec.tvadbhelper;

import org.junit.Test;

import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;

import static org.junit.Assert.*;

public class TlsRootsTest {
    private void verify(String resource, String fingerprint) throws Exception {
        X509Certificate certificate;
        try (FileInputStream input = new FileInputStream("src/main/res/raw/" + resource + ".pem")) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        assertTrue(certificate.getBasicConstraints() >= 0);
        assertEquals(certificate.getIssuerX500Principal(), certificate.getSubjectX500Principal());
        certificate.verify(certificate.getPublicKey());
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded())) {
            hex.append(String.format(Locale.US, "%02x", b & 255));
        }
        assertEquals(fingerprint, hex.toString());
    }

    @Test public void isrgRootX1MatchesPublishedRoot() throws Exception {
        verify("isrg_root_x1", "96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6");
    }

    @Test public void usertrustEccMatchesTrustedRoot() throws Exception {
        verify("usertrust_ecc", "4ff460d54b9c86dabfbcfc5712e0400d2bed3fbc4d4fbdaa86e06adcd2a9ad7a");
    }
}
