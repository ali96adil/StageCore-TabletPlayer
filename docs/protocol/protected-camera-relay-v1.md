# Protected Camera Relay v1

Compatibility is identified by the v1 wire contract, independent of the
Tablet Player APK version. Android CI reads the contract from StageCore
`dc479ade49387e445da340cce5f708d6fa950b57` on the Hub integration branch and checks the request and JPEG
framing against the same fixture as the Hub route tests.

Send `TABLET_LIVE_SHOW` with `{"media_key":"stagecore.camera.relay.v1"}`
to a v2 tablet with an active assignment. The Hub must opt into project
Camera Relay viewing and tablet TLS access. The player uses its paired
Hub endpoint and the credential of the current runtime socket. It
cancels the stream on assignment or socket changes and discards stale
frames. An expired credential triggers a runtime reconnect; the new
assignment requires a fresh live cue. The command reports completion
after the first frame is displayed.

Existing manifest and direct URL playback use their existing paths.
The protected media key is handled only by authenticated v2 live commands.
The integration branch builds and tests the debug APK in CI without
uploading it as an APK artifact. Hardware playback requires later testing.

[Hub integration and settings](https://github.com/ali96adil/StageCore/pull/534)
