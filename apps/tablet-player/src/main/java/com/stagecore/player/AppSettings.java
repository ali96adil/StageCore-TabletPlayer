package com.stagecore.player;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;
import java.util.UUID;

public final class AppSettings {
    private static final String PREFS = "stagecore-player";

    public static final String SCALE_FULL = "FULL";
    public static final String SCALE_FIT = "FIT";
    public static final String SCALE_CROP = "CROP";

    public static final String ORIENTATION_AUTO = "AUTO";
    public static final String ORIENTATION_LANDSCAPE = "LANDSCAPE";
    public static final String ORIENTATION_PORTRAIT = "PORTRAIT";

    public String deviceId;
    public String deviceName;
    public String serverHost;
    public int serverPort;
    public boolean autoDiscover;
    public int brightnessPercent;
    public String videoScaleMode;
    public String orientationMode;
    public boolean showModeOnLaunch;
    public boolean showLockEnabled;
    public boolean heartbeatEnabled;
    public int heartbeatPort;
    public int heartbeatIntervalSeconds;

    private AppSettings() {}

    public static AppSettings load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AppSettings settings = new AppSettings();
        settings.deviceId = nonBlank(prefs.getString("device_id", null), "tablet-" + UUID.randomUUID());
        settings.deviceName = nonBlank(prefs.getString("device_name", null), "Tablet " + suffix(settings.deviceId));
        settings.serverHost = prefs.getString("server_host", "");
        settings.serverPort = clamp(prefs.getInt("server_port", 8080), 1, 65535);
        settings.autoDiscover = prefs.getBoolean("auto_discover", true);
        settings.brightnessPercent = clamp(prefs.getInt("brightness_percent", 100), 5, 100);
        settings.videoScaleMode = normalizeScale(prefs.getString("video_scale_mode", SCALE_FIT));
        settings.orientationMode = normalizeOrientation(prefs.getString("orientation_mode", ORIENTATION_AUTO));
        settings.showModeOnLaunch = prefs.getBoolean("show_mode_on_launch", true);
        settings.showLockEnabled = prefs.getBoolean("show_lock_enabled", true);
        settings.heartbeatEnabled = prefs.getBoolean("heartbeat_enabled", true);
        settings.heartbeatPort = clamp(prefs.getInt("heartbeat_port", 9100), 1, 65535);
        settings.heartbeatIntervalSeconds = clamp(prefs.getInt("heartbeat_interval_seconds", 10), 3, 60);
        settings.save(context);
        return settings;
    }

    public void save(Context context) {
        deviceId = nonBlank(deviceId, "tablet-" + UUID.randomUUID());
        deviceName = nonBlank(deviceName, "Tablet " + suffix(deviceId));
        serverHost = serverHost == null ? "" : serverHost.trim();
        serverPort = clamp(serverPort, 1, 65535);
        brightnessPercent = clamp(brightnessPercent, 5, 100);
        videoScaleMode = normalizeScale(videoScaleMode);
        orientationMode = normalizeOrientation(orientationMode);
        heartbeatPort = clamp(heartbeatPort, 1, 65535);
        heartbeatIntervalSeconds = clamp(heartbeatIntervalSeconds, 3, 60);

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("device_id", deviceId)
                .putString("device_name", deviceName)
                .putString("server_host", serverHost)
                .putInt("server_port", serverPort)
                .putBoolean("auto_discover", autoDiscover)
                .putInt("brightness_percent", brightnessPercent)
                .putString("video_scale_mode", videoScaleMode)
                .putString("orientation_mode", orientationMode)
                .putBoolean("show_mode_on_launch", showModeOnLaunch)
                .putBoolean("show_lock_enabled", showLockEnabled)
                .putBoolean("heartbeat_enabled", heartbeatEnabled)
                .putInt("heartbeat_port", heartbeatPort)
                .putInt("heartbeat_interval_seconds", heartbeatIntervalSeconds)
                .apply();
    }

    public String serverLabel() {
        if (serverHost == null || serverHost.trim().isEmpty()) return "غير محدد";
        return serverHost + ":" + serverPort;
    }

    public String heartbeatLabel() {
        if (!heartbeatEnabled) return "متوقف";
        if (serverHost == null || serverHost.trim().isEmpty()) return "بانتظار السيرفر";
        return serverHost + ":" + heartbeatPort + " كل " + heartbeatIntervalSeconds + " ثواني";
    }

    private static String nonBlank(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    private static String suffix(String value) {
        if (value == null || value.length() < 6) return "01";
        return value.substring(value.length() - 6).toUpperCase(Locale.US);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String normalizeScale(String value) {
        if (SCALE_FULL.equals(value) || SCALE_FIT.equals(value) || SCALE_CROP.equals(value)) return value;
        return SCALE_FIT;
    }

    private static String normalizeOrientation(String value) {
        if (ORIENTATION_LANDSCAPE.equals(value) || ORIENTATION_PORTRAIT.equals(value) || ORIENTATION_AUTO.equals(value)) return value;
        return ORIENTATION_AUTO;
    }
}
