# Media Naming

## Local folder

Default local media folder on each Android tablet:

```text
/sdcard/TheatreVideos/
```

This path is local app/device configuration. StageCore sends logical media keys, not absolute Android filesystem paths.

## Main videos

Use six primary long-running videos:

```text
main_01.mp4
main_02.mp4
main_03.mp4
main_04.mp4
main_05.mp4
main_06.mp4
```

Each tablet can have different visual content for the same filename. Example:

```text
T1/main_01.mp4 = actor/tablet 1 version of main video 01
T2/main_01.mp4 = actor/tablet 2 version of main video 01
```

StageCore refers to the logical key:

```text
main_01
```

The tablet maps it to:

```text
main_01.mp4
```

## Overlay videos

Short secondary videos use:

```text
overlay_01.mp4
overlay_02.mp4
...
overlay_99.mp4
```

Overlay videos appear above the main video layer. The main video continues running underneath unless explicitly paused or stopped.

## Live sources

Live sources use logical keys, for example:

```text
live_camera_01
live_camera_02
```

A manifest maps each key to a network URL inside the local router/network. Example:

```text
http://192.168.3.50:8080/live.m3u8
```

The first implementation should treat live video as a best-effort layer, not frame-perfect synchronized media.

## Legacy compatibility

Older packages used:

```text
01.mp4
scene_01.mp4
```

These may remain available in Legacy OSC Compatibility Mode, but the StageCore production naming convention is:

```text
main_01.mp4
overlay_01.mp4
```
