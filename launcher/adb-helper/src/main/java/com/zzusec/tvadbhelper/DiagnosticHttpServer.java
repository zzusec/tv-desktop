package com.zzusec.tvadbhelper;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;

/** A deliberately read-only, capability-URL server for the current diagnostic report. */
public final class DiagnosticHttpServer implements Closeable {
    public static final int PORT = 8765;
    private static final int MAX_LINE = 2048;
    private static final int MAX_HEADERS = 8192;
    private static final int REQUEST_TIMEOUT_MS = 3000;

    public interface ReportSource {
        String getReport();
    }

    private final ReportSource source;
    private final String path;
    private ServerSocket listener;
    private Socket active;
    private boolean started;
    private boolean closed;

    public DiagnosticHttpServer(ReportSource source) {
        if (source == null) throw new IllegalArgumentException("report source is required");
        this.source = source;
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        StringBuilder token = new StringBuilder(48);
        for (byte value : random) {
            token.append(Character.forDigit((value >>> 4) & 15, 16));
            token.append(Character.forDigit(value & 15, 16));
        }
        path = "/" + token + "/";
    }

    public String getPath() {
        return path;
    }

    /** Bind synchronously, so callers can display bind failures instead of a false URL. */
    public synchronized void start() throws IOException {
        if (started || closed) throw new IOException("HTTP server instance already used");
        ServerSocket socket = new ServerSocket();
        try {
            socket.setReuseAddress(true);
            socket.bind(new java.net.InetSocketAddress(InetAddress.getByName("0.0.0.0"), PORT), 4);
        } catch (IOException error) {
            socket.close();
            throw error;
        }
        listener = socket;
        started = true;
        Thread worker = new Thread(() -> acceptRequests(socket), "adb-helper-http");
        worker.setDaemon(true);
        worker.start();
    }

    private void acceptRequests(ServerSocket socket) {
        while (true) {
            Socket peer = null;
            try {
                peer = socket.accept();
                synchronized (this) {
                    if (closed) {
                        peer.close();
                        return;
                    }
                    active = peer;
                }
                serve(peer);
            } catch (IOException ignored) {
                // Malformed/disconnected clients must not terminate the listener.
                if (socket.isClosed()) return;
            } finally {
                closeQuietly(peer);
                synchronized (this) {
                    if (active == peer) active = null;
                }
            }
        }
    }

    private void serve(Socket peer) throws IOException {
        long deadline = System.nanoTime() + REQUEST_TIMEOUT_MS * 1000000L;
        InputStream input = peer.getInputStream();
        String request;
        try {
            request = readLine(peer, input, deadline);
            String[] parts = request.split(" ", -1);
            if (parts.length != 3 || !parts[0].matches("[A-Z]+")
                    || !parts[1].startsWith("/")
                    || !(parts[2].equals("HTTP/1.0") || parts[2].equals("HTTP/1.1"))) {
                respond(peer, 400, "Bad Request", "text/plain", "Bad request\n");
                return;
            }
            int total = request.length() + 2;
            while (true) {
                String header = readLine(peer, input, deadline);
                total += header.length() + 2;
                if (total > MAX_HEADERS) throw new BadRequestException();
                if (header.isEmpty()) break;
                int colon = header.indexOf(':');
                if (colon <= 0 || !header.substring(0, colon).matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) {
                    throw new BadRequestException();
                }
            }
            if (!parts[0].equals("GET")) {
                respond(peer, 405, "Method Not Allowed", "text/plain", "GET only\n");
                return;
            }
            if (!(parts[1].equals(path) || parts[1].equals(path + "report.txt"))) {
                respond(peer, 404, "Not Found", "text/plain", "Not found\n");
                return;
            }
            String report;
            try {
                report = source.getReport();
                if (report == null) report = "";
            } catch (RuntimeException error) {
                respond(peer, 500, "Internal Server Error", "text/plain", "Report unavailable\n");
                return;
            }
            if (parts[1].equals(path + "report.txt")) {
                respond(peer, 200, "OK", "text/plain", report);
            } else {
                String html = "<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\">"
                        + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                        + "<title>TV ADB Diagnostic Report</title><style>"
                        + "body{max-width:72rem;margin:2rem auto;padding:0 1rem;background:#f7f5ef;color:#192c35;font-family:monospace}"
                        + "pre{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.6}"
                        + "a{color:#12666c}</style><h1>TV ADB Diagnostic Report</h1>"
                        + "<p><a href=\"report.txt\">Download report.txt</a> · "
                        + "<a href=\"./\">Refresh page</a></p><pre>" + escape(report) + "</pre></html>";
                respond(peer, 200, "OK", "text/html", html);
            }
        } catch (SocketTimeoutException error) {
            respond(peer, 408, "Request Timeout", "text/plain", "Request timeout\n");
        } catch (BadRequestException error) {
            respond(peer, 400, "Bad Request", "text/plain", "Bad request\n");
        }
    }

    private static String readLine(Socket peer, InputStream input, long deadline) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        boolean carriageReturn = false;
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new SocketTimeoutException("request deadline exceeded");
            peer.setSoTimeout((int) Math.max(1, (remaining + 999999L) / 1000000L));
            int value = input.read();
            if (value < 0) throw new BadRequestException();
            if (carriageReturn) {
                if (value != '\n') throw new BadRequestException();
                return line.toString("US-ASCII");
            }
            if (value == '\r') {
                carriageReturn = true;
            } else {
                if (value < 32 || value > 126 || line.size() >= MAX_LINE) throw new BadRequestException();
                line.write(value);
            }
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static void respond(Socket peer, int status, String reason, String type, String body)
            throws IOException {
        byte[] bytes = body.getBytes("UTF-8");
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + type + "; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'\r\n"
                + (status == 405 ? "Allow: GET\r\n" : "") + "\r\n";
        OutputStream output = peer.getOutputStream();
        output.write(headers.getBytes("US-ASCII"));
        output.write(bytes);
        output.flush();
    }

    @Override public synchronized void close() {
        closed = true;
        closeQuietly(active);
        closeQuietly(listener);
    }

    private static void closeQuietly(Closeable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException ignored) {
                // Closing a disconnected peer is harmless.
            }
        }
    }

    private static final class BadRequestException extends IOException { }
}
