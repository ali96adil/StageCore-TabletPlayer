package com.stagecore.player;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.stagecore.player.model.CommandResult;
import com.stagecore.player.model.CommandStatus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Official authenticated StageCore device transport.
 *
 * The transport is deliberately independent from legacy OSC. It authenticates
 * with the existing StageCore Companion authority, connects to the dedicated
 * Stage Device WebSocket, never replays commands after reconnect, and reports
 * device inventory observations using stagecore.device/2.
 */
public final class StageCoreDeviceConnection {
    private static final long SCOPE_ACK_RETRY_MS = 500L;
    private static final long LIVE_READY_TIMEOUT_MS = 10000L;
    private static final long HEALTH_OBSERVATION_INTERVAL_MS = 10000L;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final CommandIdTracker commandIds = new CommandIdTracker(128);

    private volatile boolean stopped;
    private volatile WebSocket socket;
    private volatile String socketDeviceId = "";
    private volatile String lastStatus = "IDLE";
    private volatile String pendingPairingCode = "";
    private volatile String assignmentState = "UNKNOWN";
    private volatile long assignmentEpoch;
    private volatile long connectionGeneration;
    private volatile String assignedProjectId = "";
    private volatile String assignedRuntimeSnapshotId = "";
    private volatile boolean runtimeAuthorityReady;
    private final AtomicLong reconnectGeneration = new AtomicLong();

    private final Object pendingLiveLock = new Object();
    private String pendingLiveCommandId = "";
    private WebSocket pendingLiveCommandSocket;
    private long pendingLiveToken;

    public StageCoreDeviceConnection(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start() {
        stopped = false;
        worker.execute(this::connectionLoop);
    }

    public void stop() {
        stopped = true;
        WebSocket current = socket;
        socket = null;
        if (current != null) current.close(1000, "tablet stopping");
        worker.shutdownNow();
    }

    public String status() { return lastStatus; }
    public String pendingPairingCode() { return pendingPairingCode; }
    public String assignmentState() { return assignmentState; }
    public long assignmentEpoch() { return assignmentEpoch; }
    public String assignedProjectId() { return assignedProjectId; }
    public String assignedRuntimeSnapshotId() { return assignedRuntimeSnapshotId; }

    public boolean runtimeReady() {
        return runtimeAuthorityReady && "ACTIVE".equals(assignmentState);
    }

    /**
     * Local/legacy playback must yield as soon as the authenticated StageCore
     * runtime channel is open, including the pre-READY assignment/scope window.
     */
    public boolean ownsPlaybackAuthority() {
        return !stopped && socket != null;
    }

    public void reconnectNow(String reason) {
        reconnectGeneration.incrementAndGet();
        pendingPairingCode = "";
        clearRuntimeScope("RECONNECTING");
        WebSocket current = socket;
        if (current != null) {
            invalidatePendingLiveCommand(current);
            current.close(1012, reason == null || reason.trim().isEmpty()
                    ? "tablet settings changed"
                    : reason.trim());
        }
    }

    private void connectionLoop() {
        long backoffMs = 1000;
        while (!stopped) {
            final long attemptGeneration = reconnectGeneration.get();
            try {
                AppSettings settings = AppSettings.load(context);
                if (settings.serverHost == null || settings.serverHost.trim().isEmpty()) {
                    lastStatus = "WAITING_FOR_SERVER";
                    sleep(1500);
                    continue;
                }
                if (!settings.hasTrustedHub()) {
                    lastStatus = "WAITING_FOR_HUB_TRUST";
                    sleep(1500);
                    continue;
                }

                StageCoreHubCandidate trustedHub = settings.trustedHubCandidate();
                OkHttpClient trustedTransport = StageCoreHubTransport.makeClient(
                        trustedHub.resolvedHost,
                        trustedHub.tlsCertificateSha256);
                String baseUrl = trustedHub.baseUrl();
                StageCoreHubIdentityVerifier.verify(baseUrl, trustedTransport, trustedHub);
                lastStatus = "HUB_VERIFIED";

                StageCoreClient descriptor = new StageCoreClient(settings.deviceId, settings.deviceName);
                StageCorePairingClient pairing = new StageCorePairingClient(
                        settings.deviceId,
                        settings.deviceName,
                        trustedTransport);
                StageCorePairingClient.Session session;
                try {
                    session = pairing.authenticate(baseUrl);
                } catch (StageCorePairingClient.StageCoreAuthException authError) {
                    if (!"COMPANION_UNPAIRED".equals(authError.errorCode)) throw authError;
                    lastStatus = "PAIRING_REQUIRED";
                    StageCorePairingClient.PairingReceipt receipt = pairing.requestPairing(
                            baseUrl,
                            descriptor.baselineCapabilities());
                    pendingPairingCode = receipt.pairingCode;
                    showPairingCode(receipt.pairingCode);
                    while (!stopped && attemptGeneration == reconnectGeneration.get()) {
                        String state = pairing.pairingStatus(baseUrl, receipt);
                        lastStatus = "PAIRING_" + state;
                        if ("APPROVED".equals(state)) break;
                        if ("REJECTED".equals(state) || "EXPIRED".equals(state)) {
                            pendingPairingCode = "";
                            throw new IllegalStateException("pairing " + state.toLowerCase());
                        }
                        sleep(1500);
                    }
                    if (stopped) return;
                    if (attemptGeneration != reconnectGeneration.get()) {
                        pendingPairingCode = "";
                        lastStatus = "RECONNECTING";
                        backoffMs = 1000;
                        continue;
                    }
                    pendingPairingCode = "";
                    session = pairing.authenticate(baseUrl);
                }
                if (attemptGeneration != reconnectGeneration.get()) {
                    pendingPairingCode = "";
                    lastStatus = "RECONNECTING";
                    backoffMs = 1000;
                    continue;
                }
                lastStatus = "AUTHENTICATED";
                connectWebSocket(baseUrl, settings, descriptor, session, trustedTransport);
                backoffMs = 1000;
                while (!stopped && socket != null) sleep(500);
            } catch (javax.net.ssl.SSLException tlsError) {
                pendingPairingCode = "";
                if (refreshTrustedHubEndpoint()) {
                    backoffMs = 1000;
                    continue;
                }
                lastStatus = "TLS_IDENTITY_MISMATCH";
                sleep(backoffMs);
                backoffMs = Math.min(15000, backoffMs * 2);
            } catch (StageCoreHubIdentityVerifier.HubIdentityException identityError) {
                pendingPairingCode = "";
                if (refreshTrustedHubEndpoint()) {
                    backoffMs = 1000;
                    continue;
                }
                lastStatus = "HUB_IDENTITY_MISMATCH";
                sleep(backoffMs);
                backoffMs = Math.min(15000, backoffMs * 2);
            } catch (java.io.IOException networkError) {
                pendingPairingCode = "";
                if (refreshTrustedHubEndpoint()) {
                    backoffMs = 1000;
                    continue;
                }
                lastStatus = "HUB_ENDPOINT_UNREACHABLE";
                sleep(backoffMs);
                backoffMs = Math.min(15000, backoffMs * 2);
            } catch (Throwable error) {
                pendingPairingCode = "";
                lastStatus = "ERROR:" + error.getClass().getSimpleName();
                sleep(backoffMs);
                backoffMs = Math.min(15000, backoffMs * 2);
            }
        }
    }

    private boolean refreshTrustedHubEndpoint() {
        AppSettings before = AppSettings.load(context);
        if (!before.autoDiscover || !before.hasTrustedHub()) return false;

        final String rememberedHubId = before.trustedHubId;
        final String rememberedFingerprint = before.trustedHubFingerprint;
        final String rememberedPin = before.trustedHubTlsSha256;
        final String previousHost = before.serverHost;
        final int previousPort = before.serverPort;

        CountDownLatch found = new CountDownLatch(1);
        AtomicReference<StageCoreHubCandidate> matched = new AtomicReference<>();
        StageCoreDiscovery discovery = new StageCoreDiscovery(context);
        lastStatus = "REDISCOVERING_TRUSTED_HUB";
        discovery.start(new StageCoreDiscovery.Callback() {
            @Override public void onFound(StageCoreHubCandidate candidate) {
                if (candidate == null) return;
                if (!candidate.matchesBinding(
                        rememberedHubId, rememberedFingerprint, rememberedPin)) {
                    return;
                }
                if (matched.compareAndSet(null, candidate)) found.countDown();
            }

            @Override public void onStatus(String message) {
                // Discovery status is intentionally not copied into runtime status:
                // this worker only needs a matching trusted endpoint or timeout.
            }
        });

        try {
            found.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            discovery.stop();
        }

        StageCoreHubCandidate candidate = matched.get();
        if (candidate == null) return false;

        if (previousHost.equals(candidate.resolvedHost)
                && previousPort == candidate.port) {
            return false;
        }
        if (!AppSettings.updateTrustedHubEndpointIfUnchanged(
                context,
                rememberedHubId,
                rememberedFingerprint,
                rememberedPin,
                previousHost,
                previousPort,
                candidate.resolvedHost,
                candidate.port)) {
            return false;
        }
        reconnectGeneration.incrementAndGet();
        lastStatus = "TRUSTED_HUB_ENDPOINT_REFRESHED";
        return true;
    }

    private void connectWebSocket(
            String baseUrl,
            AppSettings settings,
            StageCoreClient descriptor,
            StageCorePairingClient.Session session,
            OkHttpClient trustedTransport) throws Exception {
        OkHttpClient websocketClient = trustedTransport.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(10, TimeUnit.SECONDS)
                .build();
        String wsUrl = baseUrl.replaceFirst("^https://", "wss://") + "/api/v1/stage-devices/runtime";
        Request request = new Request.Builder()
                .url(wsUrl)
                .header("Authorization", "StageCoreSession " + session.token)
                .build();
        Object openedLock = new Object();
        boolean[] opened = {false};
        WebSocket ws = websocketClient.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                socket = webSocket;
                socketDeviceId = settings.deviceId;
                lastStatus = "CONNECTED";
                JSONObject hello = hello(settings, descriptor);
                webSocket.send(hello.toString());
                main.postDelayed(
                        () -> healthObservationTick(webSocket),
                        HEALTH_OBSERVATION_INTERVAL_MS);
                synchronized (openedLock) {
                    opened[0] = true;
                    openedLock.notifyAll();
                }
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                // Preserve WebSocket message order while serializing assignment
                // authority changes with UI-side command dispatch.
                main.post(() -> handleMessage(webSocket, text));
            }

            @Override public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(code, reason);
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                boolean wasCurrent = sameSocket(socket, webSocket);
                if (wasCurrent) {
                    socket = null;
                    socketDeviceId = "";
                }
                invalidatePendingLiveCommand(webSocket);
                if (wasCurrent) clearRuntimeScope("DISCONNECTED");
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                boolean wasCurrent = sameSocket(socket, webSocket);
                if (wasCurrent) {
                    socket = null;
                    socketDeviceId = "";
                }
                invalidatePendingLiveCommand(webSocket);
                if (wasCurrent) {
                    clearRuntimeScope("DISCONNECTED:" + t.getClass().getSimpleName());
                }
                synchronized (openedLock) { openedLock.notifyAll(); }
            }
        });
        synchronized (openedLock) {
            if (!opened[0]) openedLock.wait(7000);
        }
        if (!opened[0] || socket != ws) {
            ws.cancel();
            throw new IllegalStateException("StageCore WebSocket did not open");
        }
    }

    private void clearRuntimeScope(String status) {
        runtimeAuthorityReady = false;
        assignmentState = "UNKNOWN";
        assignmentEpoch = 0;
        connectionGeneration = 0;
        assignedProjectId = "";
        assignedRuntimeSnapshotId = "";
        lastStatus = status;
    }

    private JSONObject hello(AppSettings settings, StageCoreClient descriptor) {
        JSONObject json = new JSONObject();
        try {
            json.put("type", "device.hello");
            json.put("schema_version", 1);
            json.put("device_id", settings.deviceId);
            json.put("profile_id", "stagecore.tablet-player");
            json.put("device_kind", "TABLET_PLAYER");
            json.put("display_name", settings.deviceName);
            json.put("platform", "android");
            json.put("architecture", Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0]);
            json.put("client_version", BuildConfig.VERSION_NAME);
            json.put("protocol_version", StageCoreClient.PROTOCOL);
            json.put("capabilities", new JSONArray(descriptor.baselineCapabilities()));
            json.put("readiness", "BLOCKER");
            json.put("observed_state", observedStateWithHealth(false));
            json.put("network_state", new JSONObject().put("transport", "WSS"));
        } catch (Exception ignored) {}
        return json;
    }

    private void handleMessage(WebSocket webSocket, String raw) {
        try {
            String authenticatedDeviceId = deviceIdForSocket(webSocket);
            if (authenticatedDeviceId.isEmpty()) return;
            JSONObject message = new JSONObject(raw);
            if (message.optInt("schema_version", -1) != 2
                    || !authenticatedDeviceId.equals(message.optString("device_id", ""))) {
                throw new IllegalStateException("StageCore v2 envelope mismatch");
            }
            String type = message.optString("type", "");
            switch (type) {
                case "assignment.state":
                    acceptAssignmentState(webSocket, message);
                    return;
                case "tablet.assignment.prepare":
                    handleAssignmentPrepare(webSocket, message);
                    return;
                case "runtime.ready":
                    acceptRuntimeReady(webSocket, message);
                    return;
                case "command.execute":
                    handleCommand(webSocket, message);
                    return;
                case "display.state":
                    return; // Tablet Player has no Stage Display surface.
                default:
                    throw new IllegalStateException("unsupported StageCore v2 message " + type);
            }
        } catch (Exception ignored) {
            lastStatus = "PROTOCOL_ERROR";
            runtimeAuthorityReady = false;
            webSocket.close(1002, "StageCore v2 protocol error");
        }
    }

    private void acceptAssignmentState(WebSocket webSocket, JSONObject message) throws Exception {
        String state = message.optString("state", "");
        long epoch = message.optLong("assignment_epoch", 0);
        long generation = message.optLong("connection_generation", 0);
        String projectId = message.optString("project_id", "");
        String snapshotId = message.optString("runtime_snapshot_id", "");
        if (epoch < 1 || generation < 1
                || (!"UNASSIGNED".equals(state) && !"ACTIVE".equals(state))) {
            throw new IllegalStateException("unsupported tablet assignment state");
        }
        if (message.optBoolean("commands_enabled", true)) {
            throw new IllegalStateException("assignment.state cannot enable commands");
        }
        if ("UNASSIGNED".equals(state) && (!projectId.isEmpty() || !snapshotId.isEmpty())) {
            throw new IllegalStateException("unassigned tablet carries Project scope");
        }
        if ("ACTIVE".equals(state) && (projectId.isEmpty() || snapshotId.isEmpty()
                || !message.optBoolean("scope_ack_required", false))) {
            throw new IllegalStateException("active tablet scope is incomplete");
        }

        runtimeAuthorityReady = false;
        assignmentState = state;
        assignmentEpoch = epoch;
        connectionGeneration = generation;
        assignedProjectId = projectId;
        assignedRuntimeSnapshotId = snapshotId;
        lastStatus = "V2_" + state;

        if ("UNASSIGNED".equals(state)) {
            if (message.optBoolean("safe_media_required", false) && StageCoreRuntimeBridge.isReady()) {
                // handleMessage is already serialized on the main thread.
                // Enter safe media in the same authority turn so a later
                // replacement socket cannot be affected by stale queued work.
                StageCoreRuntimeBridge.enterAssignmentSafeState();
            }
            sendObservation(webSocket);
            return;
        }

        scheduleScopeAckWhenContentReady(webSocket, projectId, snapshotId, epoch, generation);
    }

    private void scheduleScopeAckWhenContentReady(
            WebSocket webSocket, String projectId, String snapshotId, long epoch, long generation) {
        main.post(() -> tryScopeAckWhenContentReady(webSocket, projectId, snapshotId, epoch, generation));
    }

    private void tryScopeAckWhenContentReady(
            WebSocket webSocket, String projectId, String snapshotId, long epoch, long generation) {
        if (stopped || socket != webSocket || runtimeAuthorityReady
                || !"ACTIVE".equals(assignmentState)
                || assignmentEpoch != epoch
                || connectionGeneration != generation
                || !assignedProjectId.equals(projectId)
                || !assignedRuntimeSnapshotId.equals(snapshotId)) {
            return;
        }
        CommandResult localContent = StageCoreRuntimeBridge.validateV2ManifestHint("");
        if (localContent.status == CommandStatus.COMPLETED) {
            sendScopeAck(webSocket, projectId, snapshotId, epoch, generation);
            return;
        }
        if (!"V2_CONTENT_NOT_READY".equals(lastStatus)) {
            lastStatus = "V2_CONTENT_NOT_READY";
            sendObservation(webSocket);
        }
        main.postDelayed(
                () -> tryScopeAckWhenContentReady(webSocket, projectId, snapshotId, epoch, generation),
                SCOPE_ACK_RETRY_MS);
    }

    private void handleAssignmentPrepare(WebSocket webSocket, JSONObject message) {
        String assignmentId = message.optString("assignment_id", "");
        String challenge = message.optString("challenge", "");
        long epoch = message.optLong("assignment_epoch", 0);
        long generation = message.optLong("connection_generation", 0);
        if (assignmentId.isEmpty() || challenge.isEmpty() || epoch != assignmentEpoch
                || generation != connectionGeneration
                || !message.optBoolean("safe_media_required", false)) {
            throw new IllegalStateException("tablet assignment prepare mismatch");
        }
        runtimeAuthorityReady = false;
        // Runtime messages are serialized on main. Perform safe-media and ACK
        // in this same authority turn instead of queueing work that could run
        // after a socket/Project transition.
        CommandResult safe = StageCoreRuntimeBridge.enterAssignmentSafeState();
        boolean ok = safe.status == CommandStatus.COMPLETED;
        try {
            String deviceId = deviceIdForSocket(webSocket);
            if (deviceId.isEmpty()
                    || assignmentEpoch != epoch
                    || connectionGeneration != generation) {
                return;
            }
            JSONObject ack = new JSONObject()
                    .put("type", "tablet.assignment.safe_ack")
                    .put("schema_version", 2)
                    .put("device_id", deviceId)
                    .put("assignment_id", assignmentId)
                    .put("assignment_epoch", epoch)
                    .put("connection_generation", generation)
                    .put("challenge", challenge)
                    .put("safe_media", ok);
            webSocket.send(ack.toString());
            lastStatus = ok ? "V2_ASSIGNMENT_SAFE" : "V2_ASSIGNMENT_SAFE_FAILED";
        } catch (Exception ignored) {
            lastStatus = "PROTOCOL_ERROR";
            webSocket.close(1002, "unable to acknowledge safe media");
        }
    }

    private void sendScopeAck(WebSocket webSocket, String projectId, String snapshotId, long epoch, long generation) {
        try {
            JSONObject ack = new JSONObject()
                    .put("type", "assignment.scope_ack")
                    .put("schema_version", 2)
                    .put("device_id", deviceIdForSocket(webSocket))
                    .put("project_id", projectId)
                    .put("runtime_snapshot_id", snapshotId)
                    .put("assignment_epoch", epoch)
                    .put("connection_generation", generation)
                    .put("readiness", "READY")
                    .put("observed_state", observedStateWithHealth(true))
                    .put("network_state", new JSONObject().put("transport", "WSS"));
            webSocket.send(ack.toString());
            lastStatus = "V2_SCOPE_ACK_SENT";
        } catch (Exception ignored) {
            lastStatus = "PROTOCOL_ERROR";
            webSocket.close(1002, "unable to acknowledge tablet scope");
        }
    }

    private void acceptRuntimeReady(WebSocket webSocket, JSONObject message) {
        if (!"ACTIVE".equals(assignmentState)
                || message.optLong("assignment_epoch", 0) != assignmentEpoch
                || message.optLong("connection_generation", 0) != connectionGeneration
                || !assignedProjectId.equals(message.optString("project_id", ""))
                || !assignedRuntimeSnapshotId.equals(message.optString("runtime_snapshot_id", ""))
                || !message.optBoolean("commands_enabled", false)) {
            throw new IllegalStateException("runtime.ready does not match active tablet scope");
        }
        runtimeAuthorityReady = true;
        lastStatus = "READY";
        sendObservation(webSocket);
    }

    private void handleCommand(WebSocket webSocket, JSONObject message) throws Exception {
        if (!runtimeAuthorityReady || !"ACTIVE".equals(assignmentState)) {
            throw new IllegalStateException("tablet runtime authority is not active");
        }
        JSONObject command = message.getJSONObject("command");
        String commandId = command.getString("command_id");
        if (commandId.trim().isEmpty()) {
            throw new IllegalStateException("command ID is empty");
        }

        String projectId = command.optString("project_id", "");
        String snapshotId = command.optString("runtime_snapshot_id", "");
        if (!assignedProjectId.equals(projectId) || !assignedRuntimeSnapshotId.equals(snapshotId)) {
            throw new IllegalStateException("command scope differs from Hub assignment");
        }
        final long acceptedEpoch = assignmentEpoch;
        final long acceptedGeneration = connectionGeneration;
        final String deadlineAt = command.optString("deadline_at", "");
        // A malformed Hub command envelope is a protocol violation; an absent
        // deadline remains valid because StageCore permits commands without one.
        if (!deadlineAt.trim().isEmpty()) {
            try {
                Instant.parse(deadlineAt.trim());
            } catch (DateTimeParseException invalidDeadline) {
                throw new IllegalStateException("invalid command deadline");
            }
        }
        JSONObject payload = command.optJSONObject("payload");
        String manifestId = payload == null ? "" : payload.optString("tablet_manifest_id", "");
        String commandType = command.optString("command_type", "");
        if (!commandIds.claim(commandId)) return;
        boolean queued = main.post(() -> {
            if (!commandScopeStillCurrent(
                    webSocket,
                    projectId,
                    snapshotId,
                    acceptedEpoch,
                    acceptedGeneration)) {
                commandIds.complete(commandId);
                if (socket == webSocket) {
                    sendResult(webSocket, commandId,
                            CommandResult.cancelled(
                                    "COMMAND_SCOPE_CHANGED",
                                    "Tablet assignment/runtime authority changed before execution"));
                    sendObservation(webSocket);
                }
                return;
            }
            if (commandDeadlineExpired(deadlineAt, System.currentTimeMillis())) {
                commandIds.complete(commandId);
                sendResult(webSocket, commandId,
                        CommandResult.timedOut(
                                "COMMAND_DEADLINE_EXPIRED",
                                "StageCore command deadline elapsed before Tablet execution"));
                sendObservation(webSocket);
                return;
            }

            CommandResult contentScope = StageCoreRuntimeBridge.validateV2ManifestHint(manifestId);
            if (contentScope.status != CommandStatus.COMPLETED) {
                commandIds.complete(commandId);
                sendResult(webSocket, commandId, contentScope);
                sendObservation(webSocket);
                return;
            }
            if ("TABLET_LIVE_SHOW".equals(commandType)) {
                startLiveCommand(webSocket, commandId, payload);
                return;
            }
            if ("TABLET_LIVE_HIDE".equals(commandType)) {
                cancelPendingLiveCommand(webSocket, "Live hidden before first frame");
            }
            CommandResult result = StageCoreRuntimeBridge.execute(commandType, payload);
            commandIds.complete(commandId);
            sendResult(webSocket, commandId, result);
            sendObservation(webSocket);
        });
        if (!queued) commandIds.abandon(commandId);
    }

    static boolean commandDeadlineExpired(String deadlineAt, long nowEpochMillis) {
        if (deadlineAt == null || deadlineAt.trim().isEmpty()) return false;
        try {
            long deadlineMillis = Instant.parse(deadlineAt.trim()).toEpochMilli();
            return nowEpochMillis >= deadlineMillis;
        } catch (DateTimeParseException invalidDeadline) {
            // Parsing is validated before queueing an authenticated Hub command.
            // Fail closed if this helper is ever called independently.
            return true;
        }
    }

    private boolean commandScopeStillCurrent(
            WebSocket webSocket,
            String projectId,
            String snapshotId,
            long epoch,
            long generation) {
        return socket == webSocket
                && commandScopeMatches(
                        runtimeAuthorityReady,
                        assignmentState,
                        assignmentEpoch,
                        connectionGeneration,
                        assignedProjectId,
                        assignedRuntimeSnapshotId,
                        projectId,
                        snapshotId,
                        epoch,
                        generation);
    }

    static boolean commandScopeMatches(
            boolean runtimeReady,
            String state,
            long currentEpoch,
            long currentGeneration,
            String currentProjectId,
            String currentSnapshotId,
            String expectedProjectId,
            String expectedSnapshotId,
            long expectedEpoch,
            long expectedGeneration) {
        return runtimeReady
                && "ACTIVE".equals(state)
                && currentEpoch == expectedEpoch
                && currentGeneration == expectedGeneration
                && currentProjectId != null
                && currentProjectId.equals(expectedProjectId)
                && currentSnapshotId != null
                && currentSnapshotId.equals(expectedSnapshotId);
    }

    private boolean isPendingLiveCommand(String commandId) {
        synchronized (pendingLiveLock) {
            return !pendingLiveCommandId.isEmpty() && pendingLiveCommandId.equals(commandId);
        }
    }

    private void startLiveCommand(WebSocket webSocket, String commandId, JSONObject payload) {
        final long token;
        synchronized (pendingLiveLock) {
            if (!pendingLiveCommandId.isEmpty()) {
                commandIds.complete(commandId);
                sendResult(webSocket, commandId,
                        CommandResult.rejected(
                                "LIVE_ALREADY_CONNECTING",
                                "Another live source is still waiting for its first frame"));
                return;
            }
            pendingLiveCommandId = commandId;
            pendingLiveCommandSocket = webSocket;
            token = ++pendingLiveToken;
        }

        CommandResult started = StageCoreRuntimeBridge.executeLiveAsync(
                payload,
                new MjpegLiveView.Listener() {
                    @Override public void onReady() {
                        finishLiveCommand(
                                webSocket,
                                commandId,
                                token,
                                CommandResult.completed("Live first frame rendered"),
                                false);
                    }

                    @Override public void onError(String reason) {
                        // MJPEG reader owns bounded reconnect/backoff. Keep the
                        // command ACCEPTED until a frame arrives or the first-
                        // frame timeout produces one terminal result.
                    }
                });
        if (started.status != CommandStatus.ACCEPTED) {
            clearPendingLiveCommand(webSocket, commandId, token);
            commandIds.complete(commandId);
            sendResult(webSocket, commandId, started);
            sendObservation(webSocket);
            return;
        }

        sendResult(webSocket, commandId, started);
        main.postDelayed(
                () -> finishLiveCommand(
                        webSocket,
                        commandId,
                        token,
                        CommandResult.timedOut(
                                "LIVE_FIRST_FRAME_TIMEOUT",
                                "Live source did not render a first frame before timeout"),
                        true),
                LIVE_READY_TIMEOUT_MS);
    }

    private void finishLiveCommand(
            WebSocket webSocket,
            String commandId,
            long token,
            CommandResult terminal,
            boolean hideLive) {
        if (!clearPendingLiveCommand(webSocket, commandId, token)) return;
        if (hideLive) {
            StageCoreRuntimeBridge.execute("TABLET_LIVE_HIDE", new JSONObject());
        }
        commandIds.complete(commandId);
        sendResult(webSocket, commandId, terminal);
        sendObservation(webSocket);
    }

    private boolean clearPendingLiveCommand(
            WebSocket webSocket, String commandId, long token) {
        synchronized (pendingLiveLock) {
            if (pendingLiveCommandSocket != webSocket
                    || !pendingLiveCommandId.equals(commandId)
                    || pendingLiveToken != token) {
                return false;
            }
            pendingLiveCommandId = "";
            pendingLiveCommandSocket = null;
            return true;
        }
    }

    private void cancelPendingLiveCommand(WebSocket webSocket, String reason) {
        String commandId;
        long token;
        synchronized (pendingLiveLock) {
            if (pendingLiveCommandSocket != webSocket || pendingLiveCommandId.isEmpty()) return;
            commandId = pendingLiveCommandId;
            token = pendingLiveToken;
        }
        finishLiveCommand(
                webSocket,
                commandId,
                token,
                CommandResult.cancelled("LIVE_CANCELLED", reason),
                false);
    }

    private void invalidatePendingLiveCommand(WebSocket webSocket) {
        boolean hidePendingLive = false;
        String interruptedCommandId = "";
        synchronized (pendingLiveLock) {
            if (pendingLiveCommandSocket != webSocket) return;
            interruptedCommandId = pendingLiveCommandId;
            pendingLiveCommandId = "";
            pendingLiveCommandSocket = null;
            pendingLiveToken++;
            hidePendingLive = true;
        }
        if (!interruptedCommandId.isEmpty()) {
            // Treat the interrupted ID as terminal locally so a replacement
            // socket cannot replay the same command ID after authority changes.
            commandIds.complete(interruptedCommandId);
        }
        if (hidePendingLive) {
            // A LIVE_SHOW that has not rendered its first frame must not finish
            // later under a replacement socket/Project scope. Release only that
            // in-flight Live attempt; already-completed Live remains untouched.
            main.post(() -> StageCoreRuntimeBridge.execute(
                    "TABLET_LIVE_HIDE", new JSONObject()));
        }
    }

    private void sendResult(WebSocket webSocket, String commandId, CommandResult result) {
        String deviceId = deviceIdForSocket(webSocket);
        if (deviceId.isEmpty()) return;
        try {
            JSONObject json = new JSONObject()
                    .put("type", "command.result")
                    .put("schema_version", 2)
                    .put("device_id", deviceId)
                    .put("command_id", commandId)
                    .put("status", result.status.toString())
                    .put("payload", new JSONObject().put("message", result.message));
            if (shouldAttachError(result.status)) {
                json.put("error", new JSONObject()
                        .put("error_code", result.code)
                        .put("category", "DEVICE")
                        .put("message", result.message)
                        .put("retryable", false));
            }
            webSocket.send(json.toString());
        } catch (Exception ignored) {}
    }

    static boolean shouldAttachError(CommandStatus status) {
        return status != CommandStatus.ACCEPTED && status != CommandStatus.COMPLETED;
    }

    private void healthObservationTick(WebSocket webSocket) {
        if (stopped || socket != webSocket) return;
        sendObservation(webSocket);
        main.postDelayed(
                () -> healthObservationTick(webSocket),
                HEALTH_OBSERVATION_INTERVAL_MS);
    }

    private JSONObject observedStateWithHealth(boolean ready) {
        JSONObject observed = ready
                ? StageCoreRuntimeBridge.assignedObservedState(
                        assignedProjectId, assignedRuntimeSnapshotId)
                : StageCoreRuntimeBridge.inventoryObservedState();
        try {
            observed.put("health", TabletHealthObservation.capture(context));
        } catch (Exception ignored) {}
        return observed;
    }

    private void sendObservation(WebSocket webSocket) {
        String deviceId = deviceIdForSocket(webSocket);
        if (deviceId.isEmpty()) return;
        try {
            boolean ready = runtimeAuthorityReady && "ACTIVE".equals(assignmentState);
            JSONObject json = new JSONObject()
                    .put("type", "device.observation")
                    .put("schema_version", 2)
                    .put("device_id", deviceId)
                    .put("readiness", ready ? "READY" : "BLOCKER")
                    .put("observed_state", observedStateWithHealth(ready))
                    .put("network_state", new JSONObject().put("transport", "WSS"));
            webSocket.send(json.toString());
        } catch (Exception ignored) {}
    }

    static boolean sameSocket(Object current, Object candidate) {
        return current != null && current == candidate;
    }

    private String deviceIdForSocket(WebSocket webSocket) {
        if (webSocket == null || socket != webSocket) return "";
        String value = socketDeviceId;
        return value == null ? "" : value.trim();
    }

    private void showPairingCode(String code) {
        main.post(() -> Toast.makeText(context, "StageCore pairing code: " + code, Toast.LENGTH_LONG).show());
    }

    private static String secureBaseUrl(AppSettings settings) {
        String host = settings.serverHost.trim();
        if (host.startsWith("http://")) {
            throw new IllegalArgumentException("StageCore device channel requires HTTPS");
        }
        if (host.startsWith("https://")) return trimSlash(host);
        if (host.contains(":")) return "https://" + trimSlash(host);
        return "https://" + host + ":" + settings.serverPort;
    }

    private static String trimSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
