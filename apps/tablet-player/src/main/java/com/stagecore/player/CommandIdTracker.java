package com.stagecore.player;

import java.util.LinkedHashSet;
import java.util.Set;

/** Bounded command-id fence for duplicate/in-flight StageCore delivery. */
final class CommandIdTracker {
    private final int maxCompleted;
    private final LinkedHashSet<String> completed = new LinkedHashSet<>();
    private final Set<String> inFlight = new LinkedHashSet<>();

    CommandIdTracker(int maxCompleted) {
        if (maxCompleted < 1) throw new IllegalArgumentException("maxCompleted must be positive");
        this.maxCompleted = maxCompleted;
    }

    synchronized boolean claim(String commandId) {
        String id = normalize(commandId);
        if (id.isEmpty() || completed.contains(id) || inFlight.contains(id)) return false;
        inFlight.add(id);
        return true;
    }

    synchronized void complete(String commandId) {
        String id = normalize(commandId);
        if (id.isEmpty()) return;
        inFlight.remove(id);
        completed.add(id);
        while (completed.size() > maxCompleted) {
            String first = completed.iterator().next();
            completed.remove(first);
        }
    }

    synchronized void abandon(String commandId) {
        String id = normalize(commandId);
        if (!id.isEmpty()) inFlight.remove(id);
    }

    synchronized boolean isInFlight(String commandId) {
        return inFlight.contains(normalize(commandId));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
