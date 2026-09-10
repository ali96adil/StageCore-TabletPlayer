package com.stagecore.player;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Build;
import android.os.Environment;

import com.stagecore.player.model.TabletManifest;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight UDP heartbeat for rehearsal/status monitoring.
 *
 * This is intentionally low-rate and best-effort. The final StageCore authenticated
 * channel will reuse the same payload fields over the trusted device protocol.
 */
public final class TabletHeartbeatReporter {
    public interface SnapshotProvider {
        AppSettings settings();
        TabletManifest manifest();
        String manifestSource();
        String mediaScanSummary();
        String storagePermissionState();
        String playerState();
        String appMode();
        String lastError();
    }

    private final Context context;
    private final SnapshotProvider provider;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;

    public TabletHeartbeatReporter(Context context, SnapshotProvider provider) {
        this.context = context.getApplicationContext();
        this.provider = provider;
    }

    public void start() {
        if (running) return;
        running = true;
        schedule(500);
    }

    public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    public void pokeSoon() {
        if (!running) return;
        schedule(250);
    }

    private void schedule(long delayMs) {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::sendThenSchedule, Math.max(0, delayMs));
    }

    private void sendThenSchedule() {
        if (!running) return;
        AppSettings settings = provider.settings();
        if (settings != null && settings.heartbeatEnabled && settings.serverHost != null && !settings.serverHost.trim().isEmpty()) {
            String payload = buildPayload(settings);
            sendUdp(settings.serverHost.trim(), settings.heartbeatPort, payload);
        }
        int seconds = settings == null ? 10 : Math.max(3, Math.min(60, settings.heartbeatIntervalSeconds));
        schedule(seconds * 1000L);
    }

    private String buildPayload(AppSettings settings) {
        TabletManifest manifest = provider.manifest();
        Battery battery = readBattery();
        return "{"
                + json("type", "tablet.health.heartbeat") + ","
                + json("protocol", StageCoreClient.PROTOCOL) + ","
                + json("device_type", StageCoreClient.DEVICE_TYPE) + ","
                + json("device_id", settings.deviceId) + ","
                + json("device_name", settings.deviceName) + ","
                + json("app_mode", safeSnapshot("app_mode", provider::appMode)) + ","
                + json("server", settings.serverLabel()) + ","
                + json("heartbeat_interval_seconds", String.valueOf(settings.heartbeatIntervalSeconds), false) + ","
                + json("battery_percent", String.valueOf(battery.percent), false) + ","
                + json("charging", String.valueOf(battery.charging), false) + ","
                + json("power_save", String.valueOf(isPowerSaveMode()), false) + ","
                + json("brightness_percent", String.valueOf(settings.brightnessPercent), false) + ","
                + json("orientation_mode", settings.orientationMode) + ","
                + json("video_scale_mode", settings.videoScaleMode) + ","
                + json("show_mode_on_launch", String.valueOf(settings.showModeOnLaunch), false) + ","
                + json("show_lock_enabled", String.valueOf(settings.showLockEnabled), false) + ","
                + json("manifest_source", safeSnapshot("manifest_source", provider::manifestSource)) + ","
                + json("project_id", manifest == null ? "unknown" : manifest.stageCoreProjectId) + ","
                + json("runtime_snapshot_id", manifest == null ? "unknown" : manifest.runtimeSnapshotId) + ","
                + json("tablet_manifest_id", manifest == null ? "unknown" : manifest.tabletManifestId) + ","
                + json("permission_state", storagePermissionState()) + ","
                + json("media_scan", safeSnapshot("media_scan", provider::mediaScanSummary)) + ","
                + json("player_state", safeSnapshot("player_state", provider::playerState)) + ","
                + json("last_error", safeSnapshot("last_error", provider::lastError))
                + "}";
    }

    private void sendUdp(String host, int port, String payload) {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length, InetAddress.getByName(host), port);
                socket.send(packet);
            } catch (Exception e) {
                android.util.Log.w("TabletHeartbeat", "heartbeat send failed: " + e.getMessage());
            }
        }, "tablet-heartbeat-send").start();
    }

    private Battery readBattery() {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return new Battery(-1, false);
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int percent = level >= 0 && scale > 0 ? Math.round((level * 100f) / scale) : -1;
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        return new Battery(percent, charging);
    }

    private boolean isPowerSaveMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false;
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager != null && powerManager.isPowerSaveMode();
    }

    private String storagePermissionState() {
        boolean allFiles = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
        return allFiles ? "مفعّل" : "غير مفعّل";
    }

    private String safeSnapshot(String field, SnapshotValue value) {
        try {
            String result = value.get();
            return result == null ? "" : result;
        } catch (Throwable throwable) {
            android.util.Log.w("TabletHeartbeat", "snapshot field failed: " + field + " — " + throwable.getClass().getSimpleName());
            return "unavailable";
        }
    }

    private interface SnapshotValue {
        String get();
    }

    private static String json(String key, String value) {
        return json(key, value, true);
    }

    private static String json(String key, String value, boolean quoted) {
        String safeValue = value == null ? "" : value;
        if (!quoted) return quote(key) + ":" + safeValue;
        return quote(key) + ":" + quote(safeValue);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static final class Battery {
        final int percent;
        final boolean charging;

        Battery(int percent, boolean charging) {
            this.percent = percent;
            this.charging = charging;
        }
    }
}
