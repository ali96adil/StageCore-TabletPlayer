package com.stagecore.player;

import com.stagecore.player.model.CommandStatus;

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
