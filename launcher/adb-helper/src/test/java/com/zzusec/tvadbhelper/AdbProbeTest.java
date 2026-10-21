package com.zzusec.tvadbhelper;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class AdbProbeTest {
    private static byte[] packet(int command, int argument, byte[] payload) {
        int checksum = 0;
        for (byte value : payload) checksum += value & 0xff;
        return ByteBuffer.allocate(24 + payload.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(command).putInt(argument).putInt(4096).putInt(payload.length)
                .putInt(checksum).putInt(command ^ -1).put(payload).array();
    }

    private AdbProbe.Result reply(byte[] bytes) throws Exception {
        return reply(bytes, 0);
    }

    private AdbProbe.Result reply(byte[] bytes, int delayMillis) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Thread responder = new Thread(() -> {
                try (Socket peer = server.accept()) {
                    peer.setSoTimeout(3000);
                    byte[] hello = new byte[31];
                    InputStream input = peer.getInputStream();
                    int offset = 0;
                    while (offset < hello.length) {
                        int read = input.read(hello, offset, hello.length - offset);
                        if (read < 0) throw new IOException("missing CNXN hello");
                        offset += read;
                    }
                    assertEquals("CNXN", new String(hello, 0, 4, "US-ASCII"));
                    assertArrayEquals(new byte[] {'h', 'o', 's', 't', ':', ':', 0},
                            Arrays.copyOfRange(hello, 24, 31));
                    if (delayMillis > 0) Thread.sleep(delayMillis);
                    if (bytes.length > 0) {
                        peer.getOutputStream().write(bytes);
                        peer.getOutputStream().flush();
                    }
                    // The diagnostic client must not send a key or an AUTH signature.
                    assertEquals(-1, input.read());
                } catch (Throwable error) {
                    failure.set(error);
                }
            }, "test-adb-responder");
            responder.setDaemon(true);
            responder.start();
            AdbProbe.Result result = AdbProbe.inspect(server.getLocalPort());
            responder.join(4000);
            assertFalse("responder leaked", responder.isAlive());
            if (failure.get() != null) throw new AssertionError("responder failed", failure.get());
            return result;
        }
    }

    @Test public void recognizesCompleteAuthPacketWithoutAuthorizing() throws Exception {
        AdbProbe.Result result = reply(packet(0x48545541, 1, new byte[20]));
        assertEquals(AdbProbe.State.AUTH, result.state);
        assertTrue(result.detail.contains("no authorization"));
    }

    @Test public void recognizesCompleteCnxnPacket() throws Exception {
        assertEquals(AdbProbe.State.CONNECTED,
                reply(packet(0x4e584e43, 0x01000000, new byte[] {'d', 'e', 'v', 'i', 'c', 'e', 0})).state);
    }

    @Test public void refusesWrongChecksum() throws Exception {
        byte[] bytes = packet(0x48545541, 1, new byte[20]);
        bytes[16] = 1;
        assertEquals(AdbProbe.State.TCP_ONLY, reply(bytes).state);
    }

    @Test public void refusesWrongMagic() throws Exception {
        byte[] bytes = packet(0x48545541, 1, new byte[20]);
        bytes[20] ^= 1;
        assertEquals(AdbProbe.State.TCP_ONLY, reply(bytes).state);
    }

    @Test public void truncatedAuthIsNotReady() throws Exception {
        byte[] bytes = Arrays.copyOf(packet(0x48545541, 1, new byte[20]), 24);
        assertEquals(AdbProbe.State.TCP_ONLY, reply(bytes, 0).state);
    }

    @Test public void arbitraryListenerIsNotAdb() throws Exception {
        byte[] bytes = new byte[24];
        Arrays.fill(bytes, (byte) 'x');
        assertEquals(AdbProbe.State.TCP_ONLY, reply(bytes).state);
    }

    @Test public void wrongAuthTypeIsNotAValidChallenge() throws Exception {
        assertEquals(AdbProbe.State.TCP_ONLY, reply(packet(0x48545541, 2, new byte[20])).state);
    }

    @Test public void boundsUnresponsiveListener() throws Exception {
        long start = System.nanoTime();
        AdbProbe.Result result = reply(new byte[0], 1600);
        assertEquals(AdbProbe.State.TCP_ONLY, result.state);
        assertTrue((System.nanoTime() - start) / 1000000 < 3500);
    }

    @Test public void refusedConnectionIsClosedNotReady() throws Exception {
        int port;
        try (ServerSocket server = new ServerSocket(0)) { port = server.getLocalPort(); }
        assertEquals(AdbProbe.State.CLOSED, AdbProbe.inspect(port).state);
    }
}
