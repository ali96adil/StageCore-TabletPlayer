package com.stagecore.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class StageCoreClient {
    public static final String PROTOCOL = "stagecore.device/2";
    public static final String DEVICE_TYPE = "TABLET_PLAYER";

    private final String deviceId;
    private final String deviceName;

    public StageCoreClient(String storedDeviceId) {
        this(storedDeviceId, "StageCore Tablet");
    }

    public StageCoreClient(String storedDeviceId, String storedDeviceName) {
        if (storedDeviceId == null || storedDeviceId.trim().isEmpty()) {
            this.deviceId = "tablet-" + UUID.randomUUID();
        } else {
            this.deviceId = storedDeviceId.trim();
        }
        if (storedDeviceName == null || storedDeviceName.trim().isEmpty()) {
            this.deviceName = "StageCore Tablet";
        } else {
            this.deviceName = storedDeviceName.trim();
        }
    }

    public String deviceId() { return deviceId; }
    public String deviceName() { return deviceName; }

    public List<String> baselineCapabilities() {
        return new ArrayList<>(Arrays.asList(
                "tablet.media.prepare",
                "tablet.media.play",
                "tablet.media.pause",
                "tablet.media.stop",
                "tablet.media.blackout",
                "tablet.media.blackout.clear",
                "tablet.media.overlay.play",
                "tablet.media.overlay.clear",
                "tablet.media.live.show",
                "tablet.media.live.hide"
        ));
    }

    public String hello() {
        return "device.hello protocol=" + PROTOCOL
                + " type=" + DEVICE_TYPE
                + " device_id=" + deviceId
                + " device_name=" + deviceName
                + " authority=HUB_OWNED_ASSIGNMENT"
                + " controllable_settings=" + StageCoreSettingsContract.supportedSettingKeys()
                + " heartbeat_schema=stagecore.tablet.health/1"
                + " heartbeat_default_interval_seconds=10"
                + " capabilities=" + baselineCapabilities();
    }
}
