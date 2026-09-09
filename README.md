# StageCore Tablet Player

Android tablet player and rehearsal remote apps for StageCore-controlled local media playback, cue execution, live/overlay layers, and legacy OSC compatibility.

## Apps

- **StageCore Player** — Android tablet endpoint for actor-worn or stage-mounted displays.
- **StageCore Remote** — lightweight rehearsal controller for direct local control when the StageCore Hub is not present.

Show-specific names such as `العميان` belong in the show configuration/media package, not the generic Android app names.

## Direction

The tablet app should not depend on fixed IP addressing or OSC as its primary production protocol. The production path is:

```text
StageCore Hub
  -> publishes a show/cue manifest to paired tablets
  -> dispatches small cue/transition commands by stable device identity
  -> records real command results and observations
```

The tablet stores and plays local media. StageCore sends logical media keys and cue IDs, not `/sdcard/...` paths.

## Key capabilities

Baseline media capabilities:

- `tablet.media.select`
- `tablet.media.prepare`
- `tablet.media.play`
- `tablet.media.pause`
- `tablet.media.stop`
- `tablet.media.blackout`

Planned layered playback capabilities:

- `tablet.media.main.play`
- `tablet.media.overlay.prepare`
- `tablet.media.overlay.play`
- `tablet.media.overlay.dissolve_in`
- `tablet.media.overlay.dissolve_out`
- `tablet.media.overlay.clear`
- `tablet.media.live.show`
- `tablet.media.live.hide`

Legacy OSC remains available as Compatibility Mode for rehearsal and fallback workflows.

## Documents

- [Tablet Player Architecture](docs/architecture/tablet-player-architecture.md)
- [Playlist/Cue Manifest v1](docs/protocol/playlist-manifest-v1.md)
- [Media Naming](docs/media/media-naming.md)
- [Roadmap](docs/roadmap.md)
