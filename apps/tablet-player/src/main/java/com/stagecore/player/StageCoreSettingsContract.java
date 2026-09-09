package com.stagecore.player;

import java.util.Arrays;
import java.util.List;

/**
 * Defines the StageCore-side control surface for tablet settings and media checks.
 *
 * This is intentionally a lightweight contract in the tablet app today. The final
 * WebSocket/pairing implementation will execute these command types through the
 * same validated StageCore command envelope used by cue playback.
 */
public final class StageCoreSettingsContract {
    private StageCoreSettingsContract() {}

    public static final String COMMAND_SETTINGS_APPLY = "tablet.settings.apply";
    public static final String COMMAND_SETTINGS_READ = "tablet.settings.read";
    public static final String COMMAND_SETTINGS_RESET = "tablet.settings.reset";
    public static final String COMMAND_MEDIA_SCAN = "tablet.media.scan";
    public static final String COMMAND_MEDIA_PREPARE_FOLDER = "tablet.media.prepare_folder";
    public static final String COMMAND_PERMISSIONS_CHECK = "tablet.permissions.check";

    public static List<String> settingsCapabilities() {
        return Arrays.asList(
                "tablet.settings.read",
                "tablet.settings.apply",
                "tablet.settings.reset",
                "tablet.settings.device_id.set",
                "tablet.settings.device_name.set",
                "tablet.settings.server.set",
                "tablet.settings.auto_discover.set",
                "tablet.settings.brightness.set",
                "tablet.settings.video_scale.set",
                "tablet.settings.orientation.set",
                "tablet.settings.show_mode.set",
                "tablet.settings.show_lock.set",
                "tablet.permissions.check",
                "tablet.media.scan",
                "tablet.media.prepare_folder"
        );
    }

    public static List<String> supportedSettingKeys() {
        return Arrays.asList(
                "device_id",
                "device_name",
                "server_host",
                "server_port",
                "auto_discover",
                "brightness_percent",
                "video_scale_mode",
                "orientation_mode",
                "show_mode_on_launch",
                "show_lock_enabled"
        );
    }
}
