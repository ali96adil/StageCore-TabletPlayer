package com.stagecore.player;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StageCoreCommandScopeFenceTest {
    @Test
    public void acceptsOnlyExactActiveRuntimeScope() {
        assertTrue(StageCoreDeviceConnection.commandScopeMatches(
                true,
                "ACTIVE",
                7,
                12,
                "project-a",
                "snapshot-21",
                "project-a",
                "snapshot-21",
                7,
                12));
    }

    @Test
    public void rejectsAuthorityChangeBeforeUiExecution() {
        assertFalse(StageCoreDeviceConnection.commandScopeMatches(
                false,
                "ACTIVE",
                7,
                12,
                "project-a",
                "snapshot-21",
                "project-a",
                "snapshot-21",
                7,
                12));
        assertFalse(StageCoreDeviceConnection.commandScopeMatches(
                true,
                "UNASSIGNED",
                8,
                13,
                "",
                "",
                "project-a",
                "snapshot-21",
                7,
                12));
        assertFalse(StageCoreDeviceConnection.commandScopeMatches(
                true,
                "ACTIVE",
                8,
                13,
                "project-b",
                "snapshot-22",
                "project-a",
                "snapshot-21",
                7,
                12));
    }

    @Test
    public void rejectsGenerationOrSnapshotDriftWithinSameProject() {
        assertFalse(StageCoreDeviceConnection.commandScopeMatches(
                true,
                "ACTIVE",
                7,
                13,
                "project-a",
                "snapshot-21",
                "project-a",
                "snapshot-21",
                7,
                12));
        assertFalse(StageCoreDeviceConnection.commandScopeMatches(
                true,
                "ACTIVE",
                7,
                12,
                "project-a",
                "snapshot-22",
                "project-a",
                "snapshot-21",
                7,
                12));
    }
}
