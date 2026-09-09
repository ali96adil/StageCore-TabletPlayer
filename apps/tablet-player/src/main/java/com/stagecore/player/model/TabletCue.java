package com.stagecore.player.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TabletCue {
    public final String tabletCueId;
    public final int tabletSequence;
    public final String sourceStageCoreCueId;
    public final int sourceStageCoreSequence;
    public final String name;
    public final List<TabletAction> actions;

    public TabletCue(String tabletCueId, int tabletSequence, String sourceStageCoreCueId, int sourceStageCoreSequence, String name, List<TabletAction> actions) {
        this.tabletCueId = tabletCueId;
        this.tabletSequence = tabletSequence;
        this.sourceStageCoreCueId = sourceStageCoreCueId;
        this.sourceStageCoreSequence = sourceStageCoreSequence;
        this.name = name;
        this.actions = Collections.unmodifiableList(new ArrayList<>(actions));
    }
}
