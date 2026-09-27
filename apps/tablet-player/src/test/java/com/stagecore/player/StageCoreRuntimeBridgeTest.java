package com.stagecore.player;

import com.stagecore.player.model.CommandStatus;

import org.json.JSONObject;
import org.junit.Test;

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
    public void assignedObservationKeepsHubScopeSeparateFromTypedPlayerState() throws Exception {
        JSONObject player = new JSONObject()
                .put("main_media", "actor-01.mp4")
                .put("main_playing", true)
                .put("live_source", "http://192.168.3.130:9081/api/v0/stream")
                .put("blackout", false);

        JSONObject observed = StageCoreRuntimeBridge.composeAssignedObservedState(
                "project-b",
                "snapshot-b",
                "tablet-manifest-01",
                player);

        assertEquals("project-b", observed.getString("project_id"));
        assertEquals("snapshot-b", observed.getString("runtime_snapshot_id"));
        assertEquals("tablet-manifest-01", observed.getString("tablet_manifest_id"));
        JSONObject typed = observed.getJSONObject("player_state");
        assertEquals("actor-01.mp4", typed.getString("main_media"));
        assertTrue(typed.getBoolean("main_playing"));
        assertFalse(typed.getBoolean("blackout"));
    }

}
