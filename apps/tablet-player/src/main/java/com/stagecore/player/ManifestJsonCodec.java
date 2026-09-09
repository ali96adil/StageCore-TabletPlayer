package com.stagecore.player;

import com.stagecore.player.model.MediaItemRef;
import com.stagecore.player.model.TabletAction;
import com.stagecore.player.model.TabletCue;
import com.stagecore.player.model.TabletManifest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ManifestJsonCodec {
    private ManifestJsonCodec() {}

    public static TabletManifest parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        String schemaVersion = root.optString("schema_version", root.optString("schemaVersion", "tablet_manifest/1"));
        String projectId = required(root, "stagecore_project_id", "stageCoreProjectId");
        String snapshotId = required(root, "runtime_snapshot_id", "runtimeSnapshotId");
        String manifestId = required(root, "tablet_manifest_id", "tabletManifestId");
        String showName = root.optString("show_name", root.optString("showName", "StageCore Show"));

        Map<String, MediaItemRef> media = parseMedia(root.getJSONObject("media"));
        JSONArray cueArray = root.optJSONArray("tablet_cues");
        if (cueArray == null) cueArray = root.optJSONArray("cues");
        if (cueArray == null) throw new JSONException("Missing required field: tablet_cues");
        List<TabletCue> cues = parseCues(cueArray);
        return new TabletManifest(schemaVersion, projectId, snapshotId, manifestId, showName, media, cues);
    }

    private static Map<String, MediaItemRef> parseMedia(JSONObject mediaObject) throws JSONException {
        Map<String, MediaItemRef> media = new LinkedHashMap<>();
        Iterator<String> keys = mediaObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject item = mediaObject.getJSONObject(key);
            String type = item.optString("type", inferMediaType(key));
            String file = nullableString(item, "file");
            String url = nullableString(item, "url");
            media.put(key, new MediaItemRef(key, type, file, url));
        }
        return media;
    }

    private static List<TabletCue> parseCues(JSONArray cueArray) throws JSONException {
        List<TabletCue> cues = new ArrayList<>();
        for (int i = 0; i < cueArray.length(); i++) {
            JSONObject cue = cueArray.getJSONObject(i);
            JSONArray actionArray = cue.optJSONArray("actions");
            List<TabletAction> actions = new ArrayList<>();
            if (actionArray != null) {
                for (int a = 0; a < actionArray.length(); a++) {
                    JSONObject action = actionArray.getJSONObject(a);
                    actions.add(new TabletAction(
                            required(action, "action_id", "actionId"),
                            required(action, "type"),
                            action.optString("media_key", action.optString("mediaKey", null)),
                            action.optInt("dissolve_in_ms", action.optInt("dissolveInMs", 0)),
                            action.optInt("dissolve_out_ms", action.optInt("dissolveOutMs", 0))
                    ));
                }
            }
            cues.add(new TabletCue(
                    required(cue, "tablet_cue_id", "tabletCueId"),
                    cue.optInt("tablet_sequence", cue.optInt("tabletSequence", i + 1)),
                    cue.optString("source_stagecore_cue_id", cue.optString("sourceStageCoreCueId", "")),
                    cue.optInt("source_stagecore_sequence", cue.optInt("sourceStageCoreSequence", -1)),
                    cue.optString("name", "Tablet Cue " + (i + 1)),
                    actions
            ));
        }
        return cues;
    }

    private static String required(JSONObject object, String... names) throws JSONException {
        for (String name : names) {
            if (object.has(name) && !object.isNull(name)) {
                String value = object.optString(name, "").trim();
                if (!value.isEmpty()) return value;
            }
        }
        throw new JSONException("Missing required field: " + names[0]);
    }

    private static String nullableString(JSONObject object, String name) {
        if (!object.has(name) || object.isNull(name)) return null;
        String value = object.optString(name, null);
        if (value == null || value.trim().isEmpty()) return null;
        return value;
    }

    private static String inferMediaType(String key) {
        if (key.startsWith("main.")) return "main";
        if (key.startsWith("overlay.")) return "overlay";
        if (key.startsWith("live.")) return "live";
        return "unknown";
    }
}
