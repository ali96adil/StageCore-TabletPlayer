# Roadmap

## Phase A — Repository foundation

- Establish repository structure.
- Document StageCore tablet/device direction.
- Define media naming rules.
- Define initial playlist/cue manifest model.
- Keep app names generic: `StageCore Player` and `StageCore Remote`.

## Phase B — Stabilize current Android apps

- Import the existing tablet player app.
- Import the existing rehearsal remote app.
- Rename app display names and package IDs.
- Keep legacy OSC compatibility working.
- Keep local media playback working without StageCore.
- Replace fragile launcher-icon workflow with deterministic PNG icon generation.

## Phase C — Layered playback

- Add Main Video layer.
- Add Overlay Video layer.
- Keep main video running underneath overlay.
- Add overlay dissolve in/out transitions.
- Add clear/blackout behavior.
- Add test cues for repeated overlay playback while main video continues.

## Phase D — Live layer

- Add live source layer.
- Support a local network URL for one live camera source.
- Add `live.show` and `live.hide` actions.
- Treat live source as best-effort timing unless transport proves tighter sync.
- Test with phone camera, IP camera, Mac relay, or StageCore relay.

## Phase E — StageCore Client

- Add `StageCoreClient` module.
- Implement stable device identity.
- Implement pairing flow once the StageCore device endpoint contract is available.
- Implement WebSocket connection and protocol versioning.
- Implement command lifecycle handling.
- Implement device observations.
- Keep UI/video/network work isolated.

## Phase F — StageCore manifest integration

- Receive/download manifest from StageCore.
- Validate local media and capabilities.
- Report READY/MISMATCH/DEGRADED clearly.
- Execute small runtime cue/transition commands by stable IDs.
- Reject stale snapshot commands.
- Prevent duplicate command execution.

## Phase G — Callboard/display mode

- Add optional display/message/countdown/alert capabilities.
- Use absolute `target_at` for countdown synchronization.
- Do not replay transient alerts/chimes after reconnect.
