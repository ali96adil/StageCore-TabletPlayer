# Project-Scoped Tablet Manifests

## Purpose

A StageCore show can contain many cues that do not affect the tablet player. The tablet should therefore receive a project-scoped local manifest containing only the tablet-relevant cues/actions for the currently assigned StageCore project/runtime snapshot.

This keeps runtime commands small and prevents cue-number collisions across different StageCore projects.

## Core Rule

The tablet never treats a human-visible cue number as globally unique.

Runtime identity is scoped by:

- `stagecore_project_id`
- `runtime_snapshot_id`
- `tablet_manifest_id`
- `tablet_cue_id` or `tablet_action_id`

A `tablet_sequence` is only a local display/order label inside one tablet manifest.

## Example Mapping

StageCore cue list:

```text
StageCore Cue 01 -> lighting only
StageCore Cue 02 -> audio only
StageCore Cue 03 -> actor entrance
...
StageCore Cue 10 -> tablet main video starts
StageCore Cue 14 -> tablet overlay dissolve
```

Tablet manifest for that StageCore project:

```text
Tablet Cue 01 -> source StageCore Cue 10 -> main_01 play
Tablet Cue 02 -> source StageCore Cue 14 -> overlay_01 dissolve in/out
```

So `Tablet Cue 01` can represent `StageCore Cue 10`.

In another StageCore project, `Tablet Cue 01` may represent a completely different StageCore cue. This is safe because the tablet validates the project and manifest identifiers before executing.

## Manifest Lifecycle

1. StageCore publishes or selects a runtime snapshot.
2. StageCore derives a tablet-specific manifest from the authoritative cue list.
3. The tablet downloads/receives the manifest and stores it locally under the project/snapshot scope.
4. The tablet validates referenced local media keys before reporting READY.
5. During rehearsal/show, StageCore sends small commands such as `prepare` or `go` that reference IDs inside the active manifest.

## Execution Model

Preferred runtime command payload:

```json
{
  "protocol": "stagecore.device/1",
  "command_id": "cmd_...",
  "command_type": "tablet.cue.go",
  "issued_at": "2026-09-09T12:20:00Z",
  "deadline_at": "2026-09-09T12:20:03Z",
  "stagecore_project_id": "project_...",
  "runtime_snapshot_id": "snapshot_...",
  "tablet_manifest_id": "tablet_manifest_...",
  "tablet_cue_id": "tablet_cue_001",
  "correlation_id": "corr_..."
}
```

The tablet must reject the command if any of these are true:

- the project ID does not match the active or loaded manifest;
- the runtime snapshot ID does not match;
- the tablet manifest ID is unknown;
- the tablet cue ID is unknown;
- the command is expired;
- the command ID was already executed;
- required media is missing or not prepared for a command that requires it.

## Multi-Project Behavior

A tablet may cache manifests for more than one StageCore project, but only one project/snapshot is active at runtime unless StageCore explicitly switches context.

When StageCore opens a different project, it must either:

- activate a matching cached manifest for that project/snapshot; or
- push a new manifest and wait for the tablet to validate readiness.

The tablet must not execute a cue from the wrong project just because the cue number or local tablet sequence happens to match.

## Commands Stay Small

After a manifest is active, StageCore should avoid sending full action definitions on every cue. It should send compact references:

- `tablet.cue.prepare`
- `tablet.cue.go`
- `tablet.cue.cancel`
- `tablet.manifest.activate`
- `tablet.manifest.sync`

The tablet resolves the referenced cue/action locally from the active manifest.

## Local Standalone Mode

Standalone rehearsal mode can still use local manifests without StageCore. In that case, the project scope is represented by a local `standalone_project_id` and the operator-selected local manifest.

Legacy OSC compatibility may map older commands to local manifest operations, but it must not bypass project/snapshot validation when StageCore mode is active.
