package com.stagecore.player;

import com.stagecore.player.model.TabletManifest;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class StageCoreClient {
    public static final String PROTOCOL = "stagecore.device/1";
    public static final String DEVICE_TYPE = "TABLET_PLAYER";

    private final String deviceId;

    public StageCoreClient(String storedDeviceId) {
        if (storedDeviceId == null || storedDeviceId.trim().isEmpty()) {
            this.deviceId = "tablet-" + UUID.randomUUID();
        } else {
            this.deviceId = storedDeviceId;
        }
    }

    public String deviceId() {
        return deviceId;
    }

    public List<String> baselineCapabilities() {
        return Arrays.asList(
                "tablet.media.prepare",
                "tablet.media.select",
                "tablet.media.play",
                "tablet.media.pause",
                "tablet.media.stop",
                "tablet.media.blackout",
                "tablet.media.overlay.play",
                "tablet.media.overlay.clear",
                "tablet.media.live.show",
                "tablet.media.live.hide"
        );
    }

    public String hello(TabletManifest manifest) {
        return "device.hello protocol=" + PROTOCOL
                + " type=" + DEVICE_TYPE
                + " device_id=" + deviceId
                + " project=" + manifest.stageCoreProjectId
                + " snapshot=" + manifest.runtimeSnapshotId
                + " manifest=" + manifest.tabletManifestId
                + " capabilities=" + baselineCapabilities();
    }
}
