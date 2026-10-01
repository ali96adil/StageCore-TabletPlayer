package com.stagecore.player;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CommandIdTrackerTest {
    @Test
    public void duplicateIsFencedWhileInFlightAndAfterCompletion() {
        CommandIdTracker tracker = new CommandIdTracker(8);

        assertTrue(tracker.claim("cmd-a"));
        assertTrue(tracker.isInFlight("cmd-a"));
        assertFalse(tracker.claim("cmd-a"));

        tracker.complete("cmd-a");
        assertFalse(tracker.isInFlight("cmd-a"));
        assertFalse(tracker.claim("cmd-a"));
    }

    @Test
    public void abandonedQueueClaimCanBeRetried() {
        CommandIdTracker tracker = new CommandIdTracker(8);

        assertTrue(tracker.claim("cmd-a"));
        tracker.abandon("cmd-a");
        assertFalse(tracker.isInFlight("cmd-a"));
        assertTrue(tracker.claim("cmd-a"));
    }

    @Test
    public void completedHistoryIsBounded() {
        CommandIdTracker tracker = new CommandIdTracker(2);
        for (String id : new String[]{"a", "b", "c"}) {
            assertTrue(tracker.claim(id));
            tracker.complete(id);
        }

        assertTrue(tracker.claim("a"));
        assertFalse(tracker.claim("b"));
        assertFalse(tracker.claim("c"));
    }
}
