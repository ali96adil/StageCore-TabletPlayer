package com.stagecore.player;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LegacyOscAuthorityTest {
    @Test
    public void legacyOscIsRehearsalOnlyWhenStageCoreOwnsPlayback() {
        assertTrue(LegacyOscServer.legacyOscAllowed(false));
        assertFalse(LegacyOscServer.legacyOscAllowed(true));
    }
}
