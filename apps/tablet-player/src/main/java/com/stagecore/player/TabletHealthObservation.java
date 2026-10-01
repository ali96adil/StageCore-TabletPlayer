package com.stagecore.player;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

import org.json.JSONObject;

/**
 * Authenticated Stage Device v2 health observation.
 *
 * Health is observational only and never grants Project/Snapshot authority.
 */
public final class TabletHealthObservation {
    private TabletHealthObservation() {}

    public static JSONObject capture(Context context) {
        Context app = context.getApplicationContext();
        AppSettings settings = AppSettings.load(app);
        Battery battery = readBattery(app);

        JSONObject health = new JSONObject();
        try {
            if (battery.percent >= 0) {
                health.put("battery_percent", battery.percent);
            }
            health.put("battery_charging", battery.charging);
            health.put("power_save", isPowerSaveMode(app));
            health.put("brightness_percent", settings.brightnessPercent);
            health.put("orientation_mode", settings.orientationMode);
            health.put("show_lock_enabled", settings.showLockEnabled);
            health.put("show_mode_on_launch", settings.showModeOnLaunch);
            health.put("keep_screen_awake", settings.keepScreenAwake);
            health.put("observed_at_ms", System.currentTimeMillis());
        } catch (Exception ignored) {}
        return health;
    }

    static int batteryPercent(int level, int scale) {
        if (level < 0 || scale <= 0) return -1;
        return Math.max(0, Math.min(100, Math.round((level * 100f) / scale)));
    }

    static boolean isChargingStatus(int status) {
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    private static Battery readBattery(Context context) {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return new Battery(-1, false);
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return new Battery(batteryPercent(level, scale), isChargingStatus(status));
    }

    private static boolean isPowerSaveMode(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false;
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager != null && powerManager.isPowerSaveMode();
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
