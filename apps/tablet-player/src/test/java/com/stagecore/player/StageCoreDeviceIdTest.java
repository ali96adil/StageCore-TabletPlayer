package com.stagecore.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.UUID;

public final class StageCoreDeviceIdTest {
    @Test
    public void generatedIdIsCanonicalCompanionUuid() {
        String id = StageCoreDeviceId.generate();

        assertEquals(36, id.length());
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    public void legacyGeneratedTabletPrefixMigratesToUuid() {
        String uuid = "799bc24c-e26b-4c78-bbe8-87b9f8103a4c";

        assertEquals(uuid, StageCoreDeviceId.normalizeGenerated("tablet-" + uuid));
    }

    @Test
    public void nonGeneratedExplicitIdIsPreserved() {
        assertEquals("tablet-001", StageCoreDeviceId.normalizeGenerated("tablet-001"));
    }
}
