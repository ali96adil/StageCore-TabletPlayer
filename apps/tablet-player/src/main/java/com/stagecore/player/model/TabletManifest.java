package com.stagecore.player.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TabletManifest {
    public final String schemaVersion;
    public final String stageCoreProjectId;
    public final String runtimeSnapshotId;
    public final String tabletManifestId;
    public final String showName;
    public final Map<String, MediaItemRef> media;
    public final List<TabletCue> cues;

    public TabletManifest(String schemaVersion, String stageCoreProjectId, String runtimeSnapshotId, String tabletManifestId, String showName, Map<String, MediaItemRef> media, List<TabletCue> cues) {
        this.schemaVersion = schemaVersion;
        this.stageCoreProjectId = stageCoreProjectId;
        this.runtimeSnapshotId = runtimeSnapshotId;
        this.tabletManifestId = tabletManifestId;
        this.showName = showName;
        this.media = new LinkedHashMap<>(media);
        this.cues = new ArrayList<>(cues);
    }

    public TabletCue cueByLocalSequence(int sequence) {
        for (TabletCue cue : cues) {
            if (cue.tabletSequence == sequence) return cue;
        }
        return null;
    }

    public TabletCue cueById(String cueId) {
        for (TabletCue cue : cues) {
            if (cue.tabletCueId.equals(cueId)) return cue;
        }
        return null;
    }
}
