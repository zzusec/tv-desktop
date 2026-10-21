package com.zzusec.tvadbhelper;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Button> buttons = new ArrayList<>();
    private AdbController controller;
    private TextView status;
    private TextView report;
    private volatile boolean destroyed;
    private boolean busy;
    private List<AdbController.Attempt> attempts = Collections.emptyList();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        controller = new AdbController(new AndroidDevice(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(20), dp(28), dp(20));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {Color.rgb(16, 28, 36), Color.rgb(22, 44, 51)}));
        root.addView(text(getString(R.string.app_name), 27, Color.rgb(242, 241, 232)));
        root.addView(text(getString(R.string.subtitle), 13, Color.rgb(174, 197, 201)));

        LinearLayout actions = new LinearLayout(this);
        LinearLayout.LayoutParams actionLayout = new LinearLayout.LayoutParams(-1, dp(55));
        actionLayout.topMargin = dp(14);
        root.addView(actions, actionLayout);
        Button refresh = button(actions, R.id.refresh, R.string.refresh);
        Button enable = button(actions, R.id.enable, R.string.enable);
        Button settings = button(actions, R.id.settings, R.string.settings);
        refresh.setOnClickListener(view -> inspect(false));
        enable.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_title).setMessage(R.string.confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm_enable, (dialog, which) -> inspect(true)).show());
        settings.setOnClickListener(view -> openSettings());

        status = text(getString(R.string.working), 15, Color.rgb(141, 217, 209));
        status.setPadding(0, dp(12), 0, dp(8));
        root.addView(status);
        root.addView(text(getString(R.string.report_header), 12, Color.rgb(174, 197, 201)));
        ScrollView scroll = new ScrollView(this);
        scroll.setId(R.id.report_scroll);
        scroll.setFocusable(true);
        scroll.setFillViewport(true);
        scroll.setNextFocusUpId(R.id.refresh);
        report = text("", 13, Color.rgb(242, 241, 232));
        report.setTypeface(Typeface.MONOSPACE);
        report.setPadding(dp(12), dp(10), dp(12), dp(14));
        report.setLineSpacing(dp(3), 1);
        report.setFocusable(false);
        scroll.addView(report, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        refresh.requestFocus();
    }

    @Override protected void onResume() {
        super.onResume();
        inspect(false);
    }

    private void inspect(boolean enable) {
        if (busy || destroyed) return;
        busy = true;
        for (Button button : buttons) button.setEnabled(false);
        status.setText(R.string.working);
        worker.execute(() -> {
            try {
                AdbController.Snapshot snapshot;
                if (enable) {
                    AdbController.EnableResult result = controller.enable();
                    attempts = result.attempts;
                    snapshot = result.after;
                } else {
                    snapshot = controller.inspect();
                }
                String rendered = render(snapshot);
                runOnUiThread(() -> {
                    if (destroyed) return;
                    report.setText(rendered);
                    int label;
                    switch (snapshot.strongestProbeState()) {
                        case AUTH: label = R.string.status_auth; break;
                        case CONNECTED: label = R.string.status_ready; break;
                        case TCP_ONLY: label = R.string.status_tcp_only; break;
                        default: label = R.string.status_closed;
                    }
                    status.setText(label);
                    finishWork();
                });
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    status.setText(getString(R.string.status_error, AdbController.describe(error)));
                    finishWork();
                });
            }
        });
    }

    private void finishWork() {
        busy = false;
        for (Button button : buttons) button.setEnabled(true);
    }

    private String render(AdbController.Snapshot snapshot) {
        boolean securePermission = checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
        StringBuilder out = new StringBuilder();
        out.append(getString(R.string.report_device, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT));
        out.append('\n').append(getString(R.string.report_network,
                snapshot.lanProbeError != null ? snapshot.lanProbeError
                        : snapshot.lanProbes.isEmpty() ? "No visible IPv4 address"
                        : android.text.TextUtils.join(", ", snapshot.lanProbes.keySet())));
        out.append('\n').append(getString(R.string.report_permission,
                getString(securePermission ? R.string.permission_granted : R.string.permission_denied)));
        out.append('\n').append(getString(R.string.report_setting, snapshot.adbSetting)).append('\n');
        for (Map.Entry<String, String> property : snapshot.properties.entrySet()) {
            out.append(property.getKey()).append(" = ")
                    .append(property.getValue().isEmpty() ? "(empty)" : property.getValue()).append('\n');
        }
        out.append('\n').append(getString(R.string.report_process, snapshot.processes));
        out.append('\n').append(getString(R.string.report_probe, snapshot.probe.detail)).append('\n');
        for (Map.Entry<String, AdbProbe.Result> entry : snapshot.lanProbes.entrySet()) {
            out.append(entry.getKey()).append(":5555 = ").append(entry.getValue().state)
                    .append("; ").append(entry.getValue().detail).append('\n');
        }
        if (!attempts.isEmpty()) {
            out.append('\n').append(getString(R.string.report_attempts)).append('\n');
            for (AdbController.Attempt attempt : attempts) {
                out.append(getString(attempt.confirmedChange ? R.string.attempt_changed : R.string.attempt_not_confirmed))
                        .append(attempt.operation).append(": ").append(attempt.detail).append('\n');
            }
        }
        out.append('\n').append(getString(R.string.report_limits));
        return out.toString();
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (ActivityNotFoundException | SecurityException primary) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
                status.setText(R.string.settings_fallback);
            } catch (ActivityNotFoundException | SecurityException fallback) {
                status.setText(getString(R.string.settings_unavailable, fallback.toString()));
            }
        }
    }

    private Button button(LinearLayout parent, int id, int label) {
        Button button = new Button(this);
        button.setId(id);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(242, 241, 232));
        button.setBackgroundResource(R.drawable.button_background);
        button.setNextFocusDownId(R.id.report_scroll);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, -1, 1);
        layout.setMargins(0, 0, dp(8), 0);
        parent.addView(button, layout);
        buttons.add(button);
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        worker.shutdownNow();
        super.onDestroy();
    }
}
