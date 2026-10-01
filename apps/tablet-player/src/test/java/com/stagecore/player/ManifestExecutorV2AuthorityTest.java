package com.stagecore.player;

import com.stagecore.player.model.CommandResult;
import com.stagecore.player.model.CommandStatus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ManifestExecutorV2AuthorityTest {
    @Test
    public void v2HubAssignmentIsNotBlockedByLegacyManifestProjectScope() throws Exception {
        ManifestStore store = new ManifestStore();
        store.loadBundledSample();
        ManifestExecutor executor = new ManifestExecutor(store, null, null);

        CommandResult legacy = executor.validateScope(
                "project_b", "snapshot_b", "");
        assertEquals(CommandStatus.REJECTED, legacy.status);

        CommandResult v2NoHint = executor.validateV2ManifestHint("");
        assertEquals(CommandStatus.COMPLETED, v2NoHint.status);

        CommandResult v2MatchingHint = executor.validateV2ManifestHint(
                "tablet_manifest_demo_001");
        assertEquals(CommandStatus.COMPLETED, v2MatchingHint.status);

        CommandResult v2WrongHint = executor.validateV2ManifestHint(
                "tablet_manifest_other");
        assertEquals(CommandStatus.REJECTED, v2WrongHint.status);
    }
}
