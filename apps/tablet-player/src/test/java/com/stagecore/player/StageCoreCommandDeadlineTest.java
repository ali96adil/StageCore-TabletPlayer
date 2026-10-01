package com.stagecore.player;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StageCoreCommandDeadlineTest {
    @Test
    public void absentDeadlineDoesNotExpireCommand() {
        assertFalse(StageCoreDeviceConnection.commandDeadlineExpired("", 1_000L));
        assertFalse(StageCoreDeviceConnection.commandDeadlineExpired(null, 1_000L));
    }

    @Test
    public void deadlineIsEnforcedAtExecutionBoundary() {
        String deadline = "2026-09-27T17:30:00Z";
        long before = java.time.Instant.parse("2026-09-27T17:29:59.999Z").toEpochMilli();
        long exact = java.time.Instant.parse(deadline).toEpochMilli();
        assertFalse(StageCoreDeviceConnection.commandDeadlineExpired(deadline, before));
        assertTrue(StageCoreDeviceConnection.commandDeadlineExpired(deadline, exact));
        assertTrue(StageCoreDeviceConnection.commandDeadlineExpired(deadline, exact + 1));
    }

    @Test
    public void malformedDeadlineFailsClosed() {
        assertTrue(StageCoreDeviceConnection.commandDeadlineExpired("not-a-time", 0L));
    }
}
