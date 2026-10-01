package com.stagecore.player;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StageCoreSocketOwnershipTest {
    @Test
    public void onlyCurrentSocketMayClearRuntimeAuthority() {
        Object current = new Object();
        Object stale = new Object();
        assertTrue(StageCoreDeviceConnection.sameSocket(current, current));
        assertFalse(StageCoreDeviceConnection.sameSocket(current, stale));
        assertFalse(StageCoreDeviceConnection.sameSocket(null, stale));
    }
}
