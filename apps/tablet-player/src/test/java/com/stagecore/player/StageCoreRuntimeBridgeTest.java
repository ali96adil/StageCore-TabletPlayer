package com.stagecore.player;

import com.stagecore.player.model.CommandResult;
import com.stagecore.player.model.CommandStatus;

import org.json.JSONObject;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StageCoreRuntimeBridgeTest {
    @Test
    public void acceptedCommandResultDoesNotCarryAnErrorObject() {
        assertFalse(StageCoreDeviceConnection.shouldAttachError(CommandStatus.ACCEPTED));
        assertFalse(StageCoreDeviceConnection.shouldAttachError(CommandStatus.COMPLETED));
        assertTrue(StageCoreDeviceConnection.shouldAttachError(CommandStatus.FAILED));
        assertTrue(StageCoreDeviceConnection.shouldAttachError(CommandStatus.TIMED_OUT));
    }

    @Test
    public void directLiveUrlAcceptsAbsoluteHttpAndHttpsWithoutCredentials() {
        assertTrue(StageCoreRuntimeBridge.isAllowedDirectLiveUrl(
                "http://192.168.3.130:9081/api/v0/stream"));
        assertTrue(StageCoreRuntimeBridge.isAllowedDirectLiveUrl(
                "https://relay.local/live.mjpeg"));

        assertFalse(StageCoreRuntimeBridge.isAllowedDirectLiveUrl(
                "http://user:pass@relay.local/live.mjpeg"));
        assertFalse(StageCoreRuntimeBridge.isAllowedDirectLiveUrl(
                "ftp://relay.local/live.mjpeg"));
        assertFalse(StageCoreRuntimeBridge.isAllowedDirectLiveUrl(
                "/api/v0/stream"));
        assertFalse(StageCoreRuntimeBridge.isAllowedDirectLiveUrl(""));
    }
    @Test
    public void mediaSelectorMustBeExclusive() {
        assertTrue(StageCoreRuntimeBridge.hasExactlyOneMediaSelector(false, true, false));
        assertFalse(StageCoreRuntimeBridge.hasExactlyOneMediaSelector(false, false, false));
        assertFalse(StageCoreRuntimeBridge.hasExactlyOneMediaSelector(true, false, true));
        assertFalse(StageCoreRuntimeBridge.hasExactlyOneMediaSelector(true, true, true));
    }

    @Test
    public void authenticatedDisplaySettingsExecuteThroughTypedContract() throws Exception {
        class FakeSettings implements StageCoreRuntimeBridge.SettingsExecutor {
            String scale = "";
            String orientation = "";
            int rotation = -1;

            @Override public CommandResult setBrightnessPercent(int percent) { return CommandResult.completed("brightness"); }
            @Override public CommandResult setShowMode(boolean enabled) { return CommandResult.completed("show"); }
            @Override public CommandResult setVideoScaleMode(String mode) {
                scale = mode;
                return CommandResult.completed("scale");
            }
            @Override public CommandResult setOrientationMode(String mode) {
                orientation = mode;
                return CommandResult.completed("orientation");
            }
            @Override public CommandResult setLiveRotationDegrees(int degrees) {
                rotation = degrees;
                return CommandResult.completed("rotation");
            }
            @Override public JSONObject observedHealth() { return new JSONObject(); }
        }

        FakeSettings settings = new FakeSettings();
        StageCoreRuntimeBridge.registerSettings(settings);
        try {
            assertEquals(CommandStatus.COMPLETED, StageCoreRuntimeBridge.execute(
                    "TABLET_VIDEO_SCALE_SET",
                    new JSONObject().put("video_scale_mode", "CROP")).status);
            assertEquals("CROP", settings.scale);

            assertEquals(CommandStatus.COMPLETED, StageCoreRuntimeBridge.execute(
                    "TABLET_ORIENTATION_SET",
                    new JSONObject().put("orientation_mode", "PORTRAIT")).status);
            assertEquals("PORTRAIT", settings.orientation);

            assertEquals(CommandStatus.COMPLETED, StageCoreRuntimeBridge.execute(
                    "TABLET_LIVE_ROTATION_SET",
                    new JSONObject().put("live_rotation_degrees", 90)).status);
            assertEquals(90, settings.rotation);

            assertEquals(CommandStatus.REJECTED, StageCoreRuntimeBridge.execute(
                    "TABLET_VIDEO_SCALE_SET",
                    new JSONObject().put("video_scale_mode", "ZOOM")).status);
            assertEquals(CommandStatus.REJECTED, StageCoreRuntimeBridge.execute(
                    "TABLET_ORIENTATION_SET",
                    new JSONObject().put("orientation_mode", "SIDEWAYS")).status);
            assertEquals(CommandStatus.REJECTED, StageCoreRuntimeBridge.execute(
                    "TABLET_LIVE_ROTATION_SET",
                    new JSONObject().put("live_rotation_degrees", 45)).status);
        } finally {
            StageCoreRuntimeBridge.unregisterSettings(settings);
        }
    }

    @Test
    public void assignedObservationKeepsHubScopeSeparateFromTypedPlayerState() {
        Map<String, Object> player = new LinkedHashMap<>();
        player.put("main_media", "actor-01.mp4");
        player.put("main_playing", true);
        player.put("live_source", "http://192.168.3.130:9081/api/v0/stream");
        player.put("blackout", false);

        Map<String, Object> observed = StageCoreRuntimeBridge.composeAssignedObservedStateValues(
                "project-b",
                "snapshot-b",
                "tablet-manifest-01",
                player);

        assertEquals("project-b", observed.get("project_id"));
        assertEquals("snapshot-b", observed.get("runtime_snapshot_id"));
        assertEquals("tablet-manifest-01", observed.get("tablet_manifest_id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) observed.get("player_state");
        assertEquals("actor-01.mp4", typed.get("main_media"));
        assertEquals(Boolean.TRUE, typed.get("main_playing"));
        assertEquals(Boolean.FALSE, typed.get("blackout"));
    }

}
