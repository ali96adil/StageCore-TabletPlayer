# Official StageCore Device Channel

## Authority

The project-independent Tablet Player transport uses `stagecore.device/2`. Legacy OSC UDP/9000 remains available for rehearsal and diagnostics, but it is not authoritative StageCore show control.

The physical tablet is paired to a trusted Hub, not to a Project. Project and Runtime Snapshot authority are owned by the Hub assignment record.

## Identity and pairing

The app keeps a P-256 signing key in Android Keystore. Pairing and trust belong to the physical tablet and trusted Hub.

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

First client message remains the transport bootstrap envelope `schema_version = 1` and advertises:

- `device_kind = TABLET_PLAYER`;
- `profile_id = stagecore.tablet-player`;
- `protocol_version = stagecore.device/2`;
- stable `device_id`;
- device metadata and implemented tablet capabilities;
- `readiness = BLOCKER`;
- device-level observed state only.

The hello does **not** contain Project ID, Runtime Snapshot ID or Tablet Manifest ID as authority. A stale local manifest cannot assign the tablet to a show.

The tablet connects and appears in Hub inventory even when the playback runtime or local manifest is not ready. Bonjour discovery and Stage Device registration therefore no longer depend on an active Project manifest.

## Hub-owned assignment

An unassigned tablet receives:

```text
assignment.state
state = UNASSIGNED
commands_enabled = false
safe_media_required = true
```

When an Operator assigns that physical tablet to a Project, StageCore chooses the exact published Runtime Snapshot. The Hub sends a one-use, authenticated request:

```text
tablet.assignment.prepare
assignment_id
assignment_epoch
connection_generation
challenge
target_project_id
target_runtime_snapshot_id
safe_media_required = true
```

Before acknowledging, the app enters the defined assignment-safe media state:

1. stop main playback;
2. hide overlay immediately;
3. hide live source;
4. enable blackout.

Only if all four operations complete does the app return `tablet.assignment.safe_ack` with the exact assignment ID, epoch, connection generation and challenge.

The Hub then atomically commits the new assignment epoch and forces a reconnect. The old socket never retains Project authority across the transition.

## ACTIVE reconnect and scope activation

After assignment, the reconnect is still projectless in `device.hello`. The Hub returns its stored authority:

```text
assignment.state
state = ACTIVE
project_id = <Hub-owned Project>
runtime_snapshot_id = <Hub-owned published Runtime Snapshot>
scope_ack_required = true
commands_enabled = false
```

The app compares that Project/Snapshot with its local active Tablet Manifest. A mismatch stays BLOCKER and never enables commands.

On an exact match the app sends `assignment.scope_ack` for the same assignment epoch and connection generation. Only then may the Hub send:

```text
runtime.ready
schema_version = 2
commands_enabled = true
```

Normal `command.execute` frames are accepted only while that exact ACTIVE Project/Snapshot/epoch authority remains current.

Tablet Manifest ID remains an optional content hint. It does not grant Project or Runtime Snapshot authority.

## Reconnect semantics

- Pairing identity is preserved across Projects.
- Authentication is reacquired before a replacement runtime channel opens.
- Hub assignment is re-read after every reconnect.
- The tablet never self-asserts a previous Project.
- The app does not replay previously received PREPARE/PLAY/overlay/live commands.
- A command interrupted by disconnect is not replayed onto the replacement socket.
- Every replacement socket must repeat the ACTIVE scope acknowledgment before commands are enabled.

## Safe failure behavior

Before ACTIVE scope acknowledgment, all v2 observations remain BLOCKER from the Hub's perspective even if a client advertises READY.

Unexpected `runtime.ready`, `command.execute`, stale epoch/generation, wrong Project/Snapshot, or malformed assignment frames fail closed.

A failed safe-media transition does not commit the assignment.

## Secure transport

Remote pairing/runtime requires HTTPS/WSS. The Android client does not install a trust-all TLS manager.

## Cross-repo contract

This client implementation is paired with StageCore Hub PR #297. The two PRs must be treated as one protocol change for deployment.

## Qualification

Source CI is necessary but not sufficient for show deployment. Physical qualification still requires Android + Pi coverage for:

- pairing and unassigned inventory visibility;
- assignment from the Stage Devices UI;
- safe-media transition;
- forced reconnect;
- ACTIVE Project/Runtime Snapshot acknowledgment;
- command execution;
- disconnect/reconnect without command replay;
- stale/wrong scope rejection.
