# Official StageCore Device Channel

## Authority

The production tablet authority path is `stagecore.device/1`. Legacy OSC UDP/9000 remains available for rehearsal and diagnostics, but it is not the authoritative StageCore control path.

## Identity and pairing

The app keeps a P-256 signing key in Android Keystore. Its public identity is exported as the uncompressed 65-byte X9.63 point and standard Base64 using StageCore algorithm identifier `P256_X963_SHA256`.

Pairing/auth endpoints:

```text
POST /api/v1/companion/pairing/requests
POST /api/v1/companion/pairing/status
POST /api/v1/companion/auth/challenges
POST /api/v1/companion/auth/sessions
```

The authentication signature is `SHA256withECDSA` over:

```text
StageCore Companion Authentication v1
<device-id>
<challenge-id>
<nonce-base64>
```

The app never stores the private key outside Android Keystore. StageCore runtime session tokens are short lived and are reacquired after disconnect/session expiry rather than being treated as permanent credentials.

## Runtime

The app connects to:

```text
wss://<hub>/api/v1/stage-devices/runtime
Authorization: StageCoreSession <session-token>
```

First client message is `device.hello` with:

- `device_kind = TABLET_PLAYER`
- `profile_id = stagecore.tablet-player`
- `protocol_version = stagecore.device/1`
- stable `device_id`
- project scope from the active tablet manifest
- advertised tablet capabilities
- readiness and initial observed state

StageCore replies with `runtime.ready`, then sends `command.execute` messages. The tablet returns `command.result` and publishes `device.observation`.

## Reconnect semantics

- Exponential reconnect is bounded.
- Authentication is reacquired before opening a replacement runtime channel.
- Command IDs completed by the process are kept in a small duplicate guard.
- The client never proactively replays a previously received command.
- StageCore may send safe persisted display state after reconnect; media commands are not replayed.

## Current media command mapping

```text
TABLET_PREPARE        -> cue/media prepare
TABLET_PLAY           -> cue/media play
TABLET_PAUSE          -> main pause
TABLET_STOP           -> main stop
TABLET_BLACKOUT       -> blackout
TABLET_BLACKOUT_CLEAR -> clear blackout
TABLET_OVERLAY_PLAY   -> overlay media
TABLET_OVERLAY_CLEAR  -> hide overlay
TABLET_LIVE_SHOW      -> show manifest live source
TABLET_LIVE_HIDE      -> hide live source
```

Playback remains owned by `ManifestExecutor`/`TabletPlayer`; the transport does not own media state.

## Secure transport

StageCore rejects remote pairing/runtime requests that are not on its secure-device transport. The Android client therefore requires HTTPS/WSS for non-loopback Hub connections and does not install a trust-all TLS manager.

## Qualification

Before calling this production-ready, CI must build the APK and the StageCore core tests must pass. Then perform one physical Android/Pi gate covering pairing approval, command execution/results, observation updates, disconnect/reconnect, session renewal/revocation, media-missing behavior, and coexistence with legacy OSC debug mode.
