package com.zzusec.tvadbhelper;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class AdbControllerTest {
    private static class FakeDevice implements AdbController.Device {
        final Map<String, String> properties = new HashMap<>();
        final List<String> writes = new ArrayList<>();
        final Map<String, AdbProbe.Result> lan = new LinkedHashMap<>();
        String setting = "0";
        boolean deny;
        boolean ignoreWrites;
        boolean failReads;
        boolean interrupted;
        AdbProbe.Result probe = new AdbProbe.Result(AdbProbe.State.CLOSED, "Connection refused");

        @Override public String property(String key) throws Exception {
            if (failReads) throw new IOException("unreadable");
            return properties.containsKey(key) ? properties.get(key) : "";
        }
        @Override public String adbSetting() { return setting; }
        @Override public String processes() { return "No visible adbd"; }
        @Override public void enableAdbSetting() {
            writes.add("adb_enabled");
            if (deny) throw new SecurityException("WRITE_SECURE_SETTINGS denied");
            if (!ignoreWrites) setting = "1";
        }
        @Override public void setProperty(String key, String value) {
            writes.add(key);
            if (deny) throw new SecurityException("property service denied");
            if (!ignoreWrites) properties.put(key, value);
        }
        @Override public AdbProbe.Result probe() { return probe; }
        @Override public Map<String, AdbProbe.Result> lanProbes() { return lan; }
        @Override public void awaitService() throws InterruptedException {
            if (interrupted) throw new InterruptedException("stopped");
        }
    }

    @Test public void permissionDeniedIsReportedAndDoesNotBecomeEnabled() throws Exception {
        FakeDevice device = new FakeDevice();
        device.deny = true;
        AdbController.EnableResult result = new AdbController(device).enable();
        assertEquals(AdbProbe.State.CLOSED, result.after.strongestProbeState());
        assertEquals("0", result.after.adbSetting);
        assertTrue(result.attempts.get(0).detail.contains("SecurityException"));
        for (AdbController.Attempt attempt : result.attempts) assertFalse(attempt.confirmedChange);
    }

    @Test public void existingRequestedValuesDoNotProvePermissionOrStart() throws Exception {
        FakeDevice device = new FakeDevice();
        device.setting = "1";
        device.properties.put("service.adb.tcp.port", "5555");
        device.properties.put("persist.adb.tcp.port", "5555");
        device.properties.put("sys.usb.netadb.config", "1");
        device.properties.put("persist.sys.usb.netadb.config", "1");
        AdbController.EnableResult result = new AdbController(device).enable();
        assertTrue(device.writes.isEmpty());
        for (AdbController.Attempt attempt : result.attempts) assertFalse(attempt.confirmedChange);
        assertEquals(AdbProbe.State.CLOSED, result.after.strongestProbeState());
    }

    @Test public void silentlyIgnoredPropertyWritesCannotReportAChange() throws Exception {
        FakeDevice device = new FakeDevice();
        device.ignoreWrites = true;
        device.properties.put("sys.usb.netadb.config", "0");
        AdbController.EnableResult result = new AdbController(device).enable();
        assertFalse(device.writes.isEmpty());
        for (AdbController.Attempt attempt : result.attempts) assertFalse(attempt.confirmedChange);
        assertEquals(AdbProbe.State.CLOSED, result.after.strongestProbeState());
    }

    @Test public void propertyChangesStillDoNotProveAServiceIsListening() throws Exception {
        FakeDevice device = new FakeDevice();
        device.properties.put("sys.usb.netadb.config", "0");
        AdbController.EnableResult result = new AdbController(device).enable();
        assertTrue(result.attempts.get(0).confirmedChange);
        assertEquals("5555", device.properties.get("service.adb.tcp.port"));
        assertEquals("1", device.properties.get("sys.usb.netadb.config"));
        assertEquals(AdbProbe.State.CLOSED, result.after.strongestProbeState());
    }

    @Test public void vendorFlagsAreNeverCreatedOnOtherFirmware() throws Exception {
        FakeDevice device = new FakeDevice();
        new AdbController(device).enable();
        assertFalse(device.writes.contains("sys.usb.netadb.config"));
        assertFalse(device.writes.contains("persist.sys.usb.netadb.config"));
    }

    @Test public void authorizationAndUsbFunctionsAreNeverWritten() throws Exception {
        FakeDevice device = new FakeDevice();
        device.properties.put("ro.adb.secure", "1");
        device.properties.put("sys.usb.config", "mtp,adb");
        new AdbController(device).enable();
        assertEquals("1", device.properties.get("ro.adb.secure"));
        assertEquals("mtp,adb", device.properties.get("sys.usb.config"));
        assertFalse(device.writes.contains("ro.adb.secure"));
        assertFalse(device.writes.contains("sys.usb.config"));
        for (String key : device.writes) assertFalse(key.startsWith("ctl."));
    }

    @Test public void readErrorsRemainVisibleAndDoNotBecomeEmptyValues() {
        FakeDevice device = new FakeDevice();
        device.failReads = true;
        AdbController.Snapshot snapshot = new AdbController(device).inspect();
        assertTrue(snapshot.properties.get("service.adb.tcp.port").contains("ERROR IOException"));
        assertEquals(AdbProbe.State.CLOSED, snapshot.strongestProbeState());
    }

    @Test public void propertyReadErrorPreventsBlindWrite() throws Exception {
        FakeDevice device = new FakeDevice();
        device.failReads = true;
        new AdbController(device).enable();
        assertEquals(Collections.singletonList("adb_enabled"), device.writes);
    }

    @Test public void lanOnlyListenerIsNotMisclassifiedAsClosed() {
        FakeDevice device = new FakeDevice();
        device.lan.put("eth0 192.0.2.4", new AdbProbe.Result(AdbProbe.State.AUTH, "AUTH"));
        AdbController.Snapshot snapshot = new AdbController(device).inspect();
        assertEquals(AdbProbe.State.CLOSED, snapshot.probe.state);
        assertEquals(AdbProbe.State.AUTH, snapshot.strongestProbeState());
    }

    @Test public void tcpAcceptWithoutAdbNeverBecomesReady() {
        FakeDevice device = new FakeDevice();
        device.lan.put("eth0 192.0.2.4", new AdbProbe.Result(AdbProbe.State.TCP_ONLY, "timeout"));
        assertEquals(AdbProbe.State.TCP_ONLY, new AdbController(device).inspect().strongestProbeState());
    }

    @Test public void lanProbeFailureIsVisibleAndDoesNotChangeLocalResult() {
        FakeDevice device = new FakeDevice() {
            @Override public Map<String, AdbProbe.Result> lanProbes() { throw new SecurityException("blocked"); }
        };
        AdbController.Snapshot snapshot = new AdbController(device).inspect();
        assertTrue(snapshot.lanProbeError.contains("SecurityException"));
        assertEquals(AdbProbe.State.CLOSED, snapshot.strongestProbeState());
    }

    @Test(expected = InterruptedException.class)
    public void cancellationDoesNotReturnAnEnableSuccess() throws Exception {
        FakeDevice device = new FakeDevice();
        device.interrupted = true;
        new AdbController(device).enable();
    }
}
