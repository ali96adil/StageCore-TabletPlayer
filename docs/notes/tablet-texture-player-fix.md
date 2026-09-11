# Tablet Texture Player Fix

Physical testing on the target Android tablet showed that the current `VideoView`/`SurfaceView` stack is not reliable enough for show use.

## Observed symptoms

- OSC packets reach the tablet.
- Heartbeat feedback reaches the server/diagnostic listener.
- `identify` works and reports a clean player state.
- `overlay.play` works, but its video surface can appear above the settings UI.
- `cue.go 1` and direct `main.play 1` can leave the main layer visually unchanged even though the command path is active.

## Decision

Replace the tablet runtime video layers with normal in-view rendering rather than relying on multiple `SurfaceView` layers. The next implementation should use `TextureView` + `MediaPlayer` as the minimal dependency-free correction, or Media3/ExoPlayer later if richer streaming support is required.

## Acceptance for the fix

- Main video appears visually on `cue.go 1` and direct `main.play 1`.
- Overlay never appears above the settings panel.
- Live never appears above the settings panel.
- Settings/status controls remain on top of media layers.
- `player_state` in heartbeat matches the visible state.
