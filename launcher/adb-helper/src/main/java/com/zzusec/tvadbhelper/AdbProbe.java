package com.zzusec.tvadbhelper;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class AdbProbe {
    enum State { AUTH, CONNECTED, TCP_ONLY, CLOSED }

    static final class Result {
        final State state;
        final String detail;

        Result(State state, String detail) {
            this.state = state;
            this.detail = detail;
        }
    }

    static Result inspect(int port) {
        return inspect("127.0.0.1", port);
    }

    static Result inspect(String host, int port) {
        boolean connected = false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 800);
            connected = true;
            socket.setSoTimeout(800);
            byte[] payload = new byte[] {'h', 'o', 's', 't', ':', ':', 0};
            int command = 0x4e584e43; // CNXN, little endian. No key or AUTH signature is sent.
            int checksum = 0;
            for (byte value : payload) checksum += value & 0xff;
            byte[] header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(command).putInt(0x01000000).putInt(4096)
                    .putInt(payload.length).putInt(checksum).putInt(command ^ -1).array();
            socket.getOutputStream().write(header);
            socket.getOutputStream().write(payload);
            socket.getOutputStream().flush();

            byte[] response = new byte[24];
            InputStream input = socket.getInputStream();
            long deadline = System.nanoTime() + 1200000000L;
            readFully(socket, input, response, deadline);
            ByteBuffer buffer = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN);
            int reply = buffer.getInt();
            int argument = buffer.getInt();
            buffer.getInt();
            int length = buffer.getInt();
            int expectedChecksum = buffer.getInt();
            int magic = buffer.getInt();
            if (magic != (reply ^ -1) || length < 0 || length > 4096) {
                return new Result(State.TCP_ONLY, "Not a valid ADB header");
            }
            byte[] body = new byte[length];
            readFully(socket, input, body, deadline);
            int actualChecksum = 0;
            for (byte value : body) actualChecksum += value & 0xff;
            if (expectedChecksum != actualChecksum) {
                return new Result(State.TCP_ONLY, "ADB packet checksum mismatch");
            }
            if (reply == 0x48545541 && argument == 1 && length == 20) {
                return new Result(State.AUTH, "ADB AUTH token received; no authorization attempted");
            }
            if (reply == 0x4e584e43 && length > 0) {
                return new Result(State.CONNECTED, "ADB CNXN received; external computer not tested");
            }
            return new Result(State.TCP_ONLY, "TCP accepted but no expected AUTH/CNXN header");
        } catch (IOException | SecurityException error) {
            return new Result(connected ? State.TCP_ONLY : State.CLOSED,
                    error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private static void readFully(Socket socket, InputStream input, byte[] bytes, long deadline)
            throws IOException {
        int offset = 0;
        while (offset < bytes.length) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new SocketTimeoutException("ADB packet deadline exceeded");
            socket.setSoTimeout((int) Math.max(1, remaining / 1000000));
            int read = input.read(bytes, offset, bytes.length - offset);
            if (read < 0) throw new IOException("EOF before complete ADB packet");
            offset += read;
        }
    }
}
