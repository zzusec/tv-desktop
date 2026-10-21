package com.zzusec.tvadbhelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AdbController {
    static final String[] PROPERTY_KEYS = {
            "init.svc.adbd", "service.adb.tcp.port", "persist.adb.tcp.port",
            "sys.usb.config", "sys.usb.state", "persist.sys.usb.config",
            "sys.usb.netadb.config", "persist.sys.usb.netadb.config", "ro.adb.secure"
    };
    private static final String[] PORT_KEYS = {"persist.adb.tcp.port", "service.adb.tcp.port"};
    private static final String[] VENDOR_KEYS = {"persist.sys.usb.netadb.config", "sys.usb.netadb.config"};

    interface Device {
        String property(String key) throws Exception;
        String adbSetting() throws Exception;
        String processes() throws Exception;
        void enableAdbSetting() throws Exception;
        void setProperty(String key, String value) throws Exception;
        AdbProbe.Result probe();
        Map<String, AdbProbe.Result> lanProbes() throws Exception;
        void awaitService() throws InterruptedException;
    }

    static final class Snapshot {
        final Map<String, String> properties = new LinkedHashMap<>();
        String adbSetting;
        String processes;
        AdbProbe.Result probe;
        final Map<String, AdbProbe.Result> lanProbes = new LinkedHashMap<>();
        String lanProbeError;

        AdbProbe.State strongestProbeState() {
            AdbProbe.State strongest = probe.state;
            for (AdbProbe.Result result : lanProbes.values()) {
                if (result.state.ordinal() < strongest.ordinal()) strongest = result.state;
            }
            return strongest;
        }
    }

    static final class Attempt {
        final String operation;
        final boolean confirmedChange;
        final String detail;

        Attempt(String operation, boolean confirmedChange, String detail) {
            this.operation = operation;
            this.confirmedChange = confirmedChange;
            this.detail = detail;
        }
    }

    static final class EnableResult {
        final List<Attempt> attempts;
        final Snapshot after;

        EnableResult(List<Attempt> attempts, Snapshot after) {
            this.attempts = attempts;
            this.after = after;
        }
    }

    private final Device device;

    AdbController(Device device) {
        this.device = device;
    }

    Snapshot inspect() {
        Snapshot snapshot = new Snapshot();
        for (String key : PROPERTY_KEYS) {
            try {
                snapshot.properties.put(key, device.property(key));
            } catch (Exception error) {
                snapshot.properties.put(key, describe(error));
            }
        }
        try {
            snapshot.adbSetting = device.adbSetting();
        } catch (Exception error) {
            snapshot.adbSetting = describe(error);
        }
        try {
            snapshot.processes = device.processes();
        } catch (Exception error) {
            snapshot.processes = describe(error);
        }
        snapshot.probe = device.probe();
        try {
            snapshot.lanProbes.putAll(device.lanProbes());
        } catch (Exception error) {
            snapshot.lanProbeError = describe(error);
        }
        return snapshot;
    }

    EnableResult enable() throws InterruptedException {
        List<Attempt> attempts = new ArrayList<>();
        // Settings permission and property-service permission are separate trust boundaries.
        try {
            if ("1".equals(device.adbSetting())) {
                attempts.add(new Attempt("adb_enabled = 1", false, "Already 1; no write, service not verified"));
            } else {
                device.enableAdbSetting();
                String value = device.adbSetting();
                attempts.add(new Attempt("adb_enabled = 1", "1".equals(value), "readback: " + value));
            }
        } catch (Exception error) {
            attempts.add(new Attempt("adb_enabled = 1", false, describe(error)));
        }
        for (String key : PORT_KEYS) setAndVerify(attempts, key, "5555");
        // Xiaomi's flags are firmware-specific: do not create them on other systems.
        for (String key : VENDOR_KEYS) {
            try {
                String original = device.property(key);
                if (!original.isEmpty()) {
                    setAndVerify(attempts, key, "1");
                } else {
                    attempts.add(new Attempt(key, false, "Absent; vendor-specific write skipped"));
                }
            } catch (Exception error) {
                attempts.add(new Attempt(key, false, describe(error)));
            }
        }
        device.awaitService();
        return new EnableResult(attempts, inspect());
    }

    private void setAndVerify(List<Attempt> attempts, String key, String value) {
        try {
            String original = device.property(key);
            if (value.equals(original)) {
                attempts.add(new Attempt(key + " = " + value, false,
                        "Already requested; no write, permission and service start not proven"));
                return;
            }
            device.setProperty(key, value);
            String actual = device.property(key);
            attempts.add(new Attempt(key + " = " + value, value.equals(actual), "readback: " + actual));
        } catch (Exception error) {
            attempts.add(new Attempt(key + " = " + value, false, describe(error)));
        }
    }

    static String describe(Exception error) {
        return "ERROR " + error.getClass().getSimpleName() + ": " + error.getMessage();
    }
}
