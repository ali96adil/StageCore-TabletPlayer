package com.stagecore.player;

import com.stagecore.player.model.CommandStatus;

import org.junit.Test;

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
}
