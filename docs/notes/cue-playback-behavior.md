# Cue Playback Behavior

This note tracks the next tablet-player slice after the validated TextureView layer fixes.

## Goal

Each tablet cue action can declare how media should behave after it starts:

- `loop`: whether the media repeats.
- `end_behavior`: what happens when non-looping media reaches the end.

Supported end behaviors:

- `none`: keep the current layer state unchanged.
- `hold`: keep the last frame visible.
- `blackout`: show blackout at completion.
- `stop`: stop the player slot and leave the stage black behind it.
- `clear`: clear/hide the layer when possible.

## Manifest example

```json
{
  "action_id": "action_main_01",
  "type": "main.play",
  "media_key": "main.01",
  "loop": false,
  "end_behavior": "blackout"
}
```

## Compatibility

Existing manifests without these fields keep the previous behavior:

- `main.play` loops by default.
- `overlay.play` clears after completion by default.
- `live.show` does not loop and has no completion behavior.
