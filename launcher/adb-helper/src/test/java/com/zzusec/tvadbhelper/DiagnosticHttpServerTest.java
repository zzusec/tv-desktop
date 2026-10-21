package com.zzusec.tvadbhelper;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class DiagnosticHttpServerTest {
    private String request(String request) throws Exception {
        try (Socket peer = new Socket("127.0.0.1", DiagnosticHttpServer.PORT)) {
            peer.setSoTimeout(5000);
            peer.getOutputStream().write(request.getBytes("US-ASCII"));
            peer.getOutputStream().flush();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            String headers;
            do {
                int value = peer.getInputStream().read();
                if (value < 0) throw new IOException("truncated HTTP headers");
                output.write(value);
                headers = output.toString("US-ASCII");
            } while (!headers.endsWith("\r\n\r\n"));
            int length = -1;
            for (String header : headers.split("\r\n")) {
                if (header.startsWith("Content-Length: ")) length = Integer.parseInt(header.substring(16));
            }
            assertTrue("missing content length", length >= 0);
            for (int i = 0; i < length; i++) {
                int value = peer.getInputStream().read();
                if (value < 0) throw new IOException("truncated HTTP body");
                output.write(value);
            }
            return output.toString("UTF-8");
        }
    }

    private String get(String path) throws Exception {
        return request("GET " + path + " HTTP/1.1\r\nHost: television\r\n\r\n");
    }

    @Test public void textUsesLatestReportAndCorrectUtf8Length() throws Exception {
        AtomicReference<String> report = new AtomicReference<>("old");
        try (DiagnosticHttpServer server = new DiagnosticHttpServer(report::get)) {
            server.start();
            report.set("current \u4e2d\u6587\n");
            String response = get(server.getPath() + "report.txt");
            assertTrue(response.startsWith("HTTP/1.1 200 OK\r\n"));
            assertTrue(response.endsWith(report.get()));
            assertTrue(response.contains("Content-Length: " + report.get().getBytes("UTF-8").length + "\r\n"));
            assertTrue(response.contains("Cache-Control: no-store\r\n"));
            assertTrue(response.contains("Referrer-Policy: no-referrer\r\n"));
            assertTrue(response.contains("X-Content-Type-Options: nosniff\r\n"));
        }
    }

    @Test public void htmlEscapesReportAndDisablesScripts() throws Exception {
        try (DiagnosticHttpServer server = new DiagnosticHttpServer(() -> "<script>&\"'")) {
            server.start();
            String response = get(server.getPath());
            assertTrue(response.startsWith("HTTP/1.1 200"));
            assertTrue(response.contains("&lt;script&gt;&amp;&quot;&#39;"));
            assertFalse(response.contains("<script>"));
            assertTrue(response.contains("Content-Security-Policy: default-src 'none';"));
            assertTrue(response.contains("href=\"report.txt\""));
        }
    }

    @Test public void wrongTokenAndNonExactPathsNeverReadReport() throws Exception {
        try (DiagnosticHttpServer server = new DiagnosticHttpServer(() -> {
            fail("unauthorized request read report");
            return "secret";
        })) {
            server.start();
            String[] paths = { "/", "/wrong/report.txt", server.getPath() + "?x=1",
                    server.getPath() + "../report.txt", server.getPath() + "report.txt/",
                    server.getPath().replace("/", "%2f") };
            for (String path : paths) {
                String response = get(path.startsWith("/") ? path : "/" + path);
                assertTrue(path, response.startsWith("HTTP/1.1 404"));
                assertFalse(response.contains(server.getPath()));
                assertFalse(response.contains("secret"));
            }
        }
    }

    @Test public void postIsNeverExecuted() throws Exception {
        try (DiagnosticHttpServer server = new DiagnosticHttpServer(() -> {
            fail("POST read report");
            return "secret";
        })) {
            server.start();
            String response = request("POST " + server.getPath() + " HTTP/1.1\r\nHost: tv\r\n\r\n");
            assertTrue(response.startsWith("HTTP/1.1 405"));
            assertTrue(response.contains("Allow: GET\r\n"));
        }
    }

    @Test public void rejectsOversizedRequestAndContinuesServing() throws Exception {
        try (DiagnosticHttpServer server = new DiagnosticHttpServer(() -> "ready")) {
            server.start();
            StringBuilder large = new StringBuilder("GET /");
            for (int i = 0; i < 2200; i++) large.append('a');
            large.append(" HTTP/1.1\r\n\r\n");
            assertTrue(request(large.toString()).startsWith("HTTP/1.1 400"));
            assertTrue(get(server.getPath()).startsWith("HTTP/1.1 200"));
        }
    }

    @Test public void rejectsExcessiveTotalHeaders() throws Exception {
        try (DiagnosticHttpServer server = new DiagnosticHttpServer(() -> "ready")) {
            server.start();
            StringBuilder headers = new StringBuilder("GET " + server.getPath() + " HTTP/1.1\r\n");
            StringBuilder value = new StringBuilder();
            for (int i = 0; i < 1000; i++) value.append('b');
            for (int i = 0; i < 9; i++) headers.append("X-Test: ").append(value).append("\r\n");
            headers.append("\r\n");
            assertTrue(request(headers.toString()).startsWith("HTTP/1.1 400"));
        }
    }

    @Test public void rejectsMalformedHeadersAndRequestLine() throws Exception {
        try (DiagnosticHttpServer server = new DiagnosticHttpServer(() -> "ready")) {
            server.start();
            assertTrue(request("GET / HTTP/2.0\r\n\r\n").startsWith("HTTP/1.1 400"));
            assertTrue(request("GET / HTTP/1.1\r\nMissingColon\r\n\r\n").startsWith("HTTP/1.1 400"));
            assertTrue(request("GET / HTTP/1.1\n\n").startsWith("HTTP/1.1 400"));
            assertTrue(get(server.getPath()).startsWith("HTTP/1.1 200"));
        }
    }

    @Test public void reportFailureIsVisibleWithoutExceptionDisclosure() throws Exception {
        try (DiagnosticHttpServer server = new DiagnosticHttpServer(() -> {
            throw new IllegalStateException("private detail");
        })) {
            server.start();
            String response = get(server.getPath());
            assertTrue(response.startsWith("HTTP/1.1 500"));
            assertTrue(response.endsWith("Report unavailable\n"));
            assertFalse(response.contains("private detail"));
        }
    }

    @Test public void incompleteRequestHasAbsoluteTimeout() throws Exception {
        try (DiagnosticHttpServer server = new DiagnosticHttpServer(() -> "ready")) {
            server.start();
            long start = System.nanoTime();
            assertTrue(request("GET ").startsWith("HTTP/1.1 408"));
            long elapsed = (System.nanoTime() - start) / 1000000L;
            assertTrue("timeout too long: " + elapsed, elapsed < 4500);
        }
    }

    @Test public void closeClosesActiveConnectionAndReleasesPort() throws Exception {
        DiagnosticHttpServer server = new DiagnosticHttpServer(() -> "ready");
        server.start();
        try (Socket peer = new Socket("127.0.0.1", DiagnosticHttpServer.PORT)) {
            peer.setSoTimeout(1500);
            peer.getOutputStream().write("GET ".getBytes("US-ASCII"));
            Thread.sleep(100);
            server.close();
            assertEquals(-1, peer.getInputStream().read());
        } finally {
            server.close();
        }
        try (ServerSocket rebound = new ServerSocket()) {
            rebound.setReuseAddress(true);
            rebound.bind(new java.net.InetSocketAddress(InetAddress.getByName("0.0.0.0"), DiagnosticHttpServer.PORT));
        }
    }

    @Test public void bindFailureIsSynchronous() throws Exception {
        try (DiagnosticHttpServer first = new DiagnosticHttpServer(() -> "first");
             DiagnosticHttpServer second = new DiagnosticHttpServer(() -> "second")) {
            first.start();
            try {
                second.start();
                fail("duplicate bind succeeded");
            } catch (IOException expected) {
                assertTrue(get(first.getPath()).endsWith("</html>"));
            }
        }
    }

    @Test public void tokensAreIndependent192BitCapabilities() {
        try (DiagnosticHttpServer first = new DiagnosticHttpServer(() -> "");
             DiagnosticHttpServer second = new DiagnosticHttpServer(() -> "")) {
            assertTrue(first.getPath().matches("/[0-9a-f]{48}/"));
            assertNotEquals(first.getPath(), second.getPath());
        }
    }
}
