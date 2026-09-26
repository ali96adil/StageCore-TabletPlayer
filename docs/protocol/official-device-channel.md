# Official StageCore Device Channel

## Authority

The next project-independent tablet transport uses `stagecore.device/2`. Legacy OSC UDP/9000 remains available for rehearsal and diagnostics, but it is not authoritative StageCore show control.

This branch implements **device identity / inventory bootstrap only**. It deliberately does not enable tablet show commands until the Hub-owned tablet assignment handshake is implemented end to end.

## Identity and pairing

The app keeps a P-256 signing key in Android Keystore. Pairing and trust belong to the physical tablet and trusted Hub, not to a StageCore Project.

Pairing/auth endpoints remain:

```text
POST /api/v1/companion/pairing/requests
POST /api/v1/companion/pairing/status
POST /api/v1/companion/auth/challenges
POST /api/v1/companion/auth/sessions
```

The app never stores the private key outside Android Keystore. Runtime session tokens are reacquired after disconnect/session expiry rather than treated as permanent credentials.

## Project-independent v2 bootstrap

The app connects to:

```text
wss://<hub>/api/v1/stage-devices/runtime
Authorization: StageCoreSession <session-token>
```

First client message is `device.hello` with:

- `device_kind = TABLET_PLAYER`;
- `profile_id = stagecore.tablet-player`;
- `protocol_version = stagecore.device/2`;
- stable `device_id`;
- device metadata and implemented tablet capabilities;
- `readiness = BLOCKER` until Hub assignment authority is active;
- device-level observed state only.

The v2 hello **does not contain Project ID, Runtime Snapshot ID or Tablet Manifest ID as authority**. A stale local manifest cannot move the tablet into another Project.

The tablet may connect even when the playback runtime or a local manifest is not ready. This is intentional: the Hub must be able to discover and inventory the physical tablet independently from show content.

## Hub-owned assignment bootstrap

For this slice the Hub may return only:

- `assignment.state / UNASSIGNED`, with no Project;
- `assignment.state / BLOCKED`, with the Hub-owned Project ID and assignment epoch;
- `commands_enabled = false`.

The tablet validates those invariants and publishes a schema-v2 blocker observation. It does not persist the Project as device identity.

Receiving `runtime.ready` or `command.execute` before the future tablet-assignment activation handshake is implemented is treated as a protocol violation and fails closed. This prevents a projectless bootstrap build from accidentally executing stale v1 show commands.

## Reconnect semantics

- Pairing identity is preserved across Projects.
- Authentication is reacquired before opening a replacement runtime channel.
- The client never replays a previously received show command.
- Hub assignment is re-read after reconnect; the tablet does not self-assert a previous Project.
- Local media manifests remain content/runtime data, not device ownership.

## Future activation contract

A subsequent Hub + Tablet slice must add a tablet-specific safe-state / assignment activation handshake. That handshake must:

1. place the player into a defined safe media state;
2. fence commands from the old Project/Snapshot;
3. bind the exact Hub-owned Project, Runtime Snapshot and Tablet Manifest scope;
4. require acknowledgment before READY;
5. never replay PREPARE/PLAY/overlay/live commands after transfer or reconnect.

Only after that contract is implemented and qualified may `stagecore.device/2` accept normal tablet media commands.

## Secure transport

Remote pairing/runtime still requires HTTPS/WSS. The Android client does not install a trust-all TLS manager.

## Qualification

This source slice is not production-ready by CI alone. Before deployment it requires the matching StageCore Hub v2 assignment implementation and a physical Android/Pi gate covering pairing, unassigned inventory visibility, assignment, scope fencing, command execution, disconnect/reconnect and stale-command rejection.
