package com.stagecore.player;

import java.util.UUID;

/**
 * StageCore reuses the Companion authentication authority for stage devices.
 * Companion IDs are canonical UUID strings, so automatically generated tablet
 * identities must remain 36-character UUIDs.
 */
final class StageCoreDeviceId {
    private static final String LEGACY_GENERATED_PREFIX = "tablet-";

    private StageCoreDeviceId() {}

    static String generate() {
        return UUID.randomUUID().toString();
    }

    /**
     * Migrates only the historical automatically generated tablet-<uuid> form.
     * Other explicit values are preserved so this helper never silently
     * rewrites operator-entered identifiers.
     */
    static String normalizeGenerated(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (!trimmed.startsWith(LEGACY_GENERATED_PREFIX)) return trimmed;

        String candidate = trimmed.substring(LEGACY_GENERATED_PREFIX.length());
        try {
            String canonical = UUID.fromString(candidate).toString();
            return canonical.equalsIgnoreCase(candidate) ? canonical : trimmed;
        } catch (IllegalArgumentException ignored) {
            return trimmed;
        }
    }
}
