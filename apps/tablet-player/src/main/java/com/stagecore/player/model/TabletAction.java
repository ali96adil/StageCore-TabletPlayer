package com.stagecore.player.model;

public final class TabletAction {
    public static final String END_NONE = "none";
    public static final String END_HOLD = "hold";
    public static final String END_BLACKOUT = "blackout";
    public static final String END_STOP = "stop";
    public static final String END_CLEAR = "clear";

    public final String actionId;
    public final String type;
    public final String mediaKey;
    public final int dissolveInMs;
    public final int dissolveOutMs;
    public final boolean loop;
    public final String endBehavior;

    public TabletAction(String actionId, String type, String mediaKey, int dissolveInMs, int dissolveOutMs) {
        this(actionId, type, mediaKey, dissolveInMs, dissolveOutMs, defaultLoop(type), defaultEndBehavior(type));
    }

    public TabletAction(String actionId, String type, String mediaKey, int dissolveInMs, int dissolveOutMs, boolean loop, String endBehavior) {
        this.actionId = actionId;
        this.type = type;
        this.mediaKey = mediaKey;
        this.dissolveInMs = dissolveInMs;
        this.dissolveOutMs = dissolveOutMs;
        this.loop = loop;
        this.endBehavior = normalizeEndBehavior(endBehavior, defaultEndBehavior(type));
    }

    public static boolean defaultLoop(String type) {
        return "main.play".equals(type);
    }

    public static String defaultEndBehavior(String type) {
        if ("overlay.play".equals(type)) return END_CLEAR;
        return END_NONE;
    }

    public static String normalizeEndBehavior(String value, String fallback) {
        if (value == null) return fallback;
        String normalized = value.trim().toLowerCase(java.util.Locale.US);
        if (normalized.isEmpty()) return fallback;
        switch (normalized) {
            case END_NONE:
            case END_HOLD:
            case END_BLACKOUT:
            case END_STOP:
            case END_CLEAR:
                return normalized;
            default:
                return fallback;
        }
    }
}
