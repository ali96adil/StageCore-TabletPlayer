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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Set<String> completedCommandIds = new LinkedHashSet<>();

    private volatile boolean stopped;
    private volatile WebSocket socket;
    private volatile String lastStatus = "IDLE";
    private volatile String pendingPairingCode = "";
    private volatile String assignmentState = "UNKNOWN";
    private volatile long assignmentEpoch;
    private volatile long connectionGeneration;
    private volatile String assignedProjectId = "";
    private volatile String assignedRuntimeSnapshotId = "";
    private volatile boolean runtimeAuthorityReady;

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

    private void connectionLoop() {
        long backoffMs = 1000;
        while (!stopped) {
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
                    while (!stopped) {
                        String state = pairing.pairingStatus(baseUrl, receipt);
                        lastStatus = "PAIRING_" + state;
                        if ("APPROVED".equals(state)) break;
                        if ("REJECTED".equals(state) || "EXPIRED".equals(state)) {
                            throw new IllegalStateException("pairing " + state.toLowerCase());
                        }
                        sleep(1500);
                    }
                    if (stopped) return;
                    pendingPairingCode = "";
                    session = pairing.authenticate(baseUrl);
                }
                lastStatus = "AUTHENTICATED";
                connectWebSocket(baseUrl, settings, descriptor, session, trustedTransport);
                backoffMs = 1000;
                while (!stopped && socket != null) sleep(500);
            } catch (javax.net.ssl.SSLException tlsError) {
                lastStatus = "TLS_IDENTITY_MISMATCH";
                sleep(backoffMs);
                backoffMs = Math.min(15000, backoffMs * 2);
            } catch (StageCoreHubIdentityVerifier.HubIdentityException identityError) {
                lastStatus = "HUB_IDENTITY_MISMATCH";
                sleep(backoffMs);
                backoffMs = Math.min(15000, backoffMs * 2);
            } catch (Throwable error) {
                lastStatus = "ERROR:" + error.getClass().getSimpleName();
                sleep(backoffMs);
                backoffMs = Math.min(15000, backoffMs * 2);
            }
        }
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
                lastStatus = "CONNECTED";
                JSONObject hello = hello(settings, descriptor);
                webSocket.send(hello.toString());
                synchronized (openedLock) {
                    opened[0] = true;
                    openedLock.notifyAll();
                }
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                handleMessage(webSocket, text);
            }

            @Override public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(code, reason);
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                if (socket == webSocket) socket = null;
                invalidatePendingLiveCommand(webSocket);
                runtimeAuthorityReady = false;
                connectionGeneration = 0;
                lastStatus = "DISCONNECTED";
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (socket == webSocket) socket = null;
                invalidatePendingLiveCommand(webSocket);
                runtimeAuthorityReady = false;
                connectionGeneration = 0;
                lastStatus = "DISCONNECTED:" + t.getClass().getSimpleName();
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
            json.put("observed_state", StageCoreRuntimeBridge.inventoryObservedState());
            json.put("network_state", new JSONObject().put("transport", "WSS"));
        } catch (Exception ignored) {}
        return json;
    }

    private void handleMessage(WebSocket webSocket, String raw) {
        try {
            JSONObject message = new JSONObject(raw);
            if (message.optInt("schema_version", -1) != 2
                    || !settingsDeviceId().equals(message.optString("device_id", ""))) {
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
                main.post(() -> {
                    StageCoreRuntimeBridge.enterAssignmentSafeState();
                    sendObservation(webSocket);
                });
            } else {
                sendObservation(webSocket);
            }
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
        main.post(() -> {
            CommandResult safe = StageCoreRuntimeBridge.enterAssignmentSafeState();
            boolean ok = safe.status == CommandStatus.COMPLETED;
            try {
                JSONObject ack = new JSONObject()
                        .put("type", "tablet.assignment.safe_ack")
                        .put("schema_version", 2)
                        .put("device_id", settingsDeviceId())
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
        });
    }

    private void sendScopeAck(WebSocket webSocket, String projectId, String snapshotId, long epoch, long generation) {
        try {
            JSONObject ack = new JSONObject()
                    .put("type", "assignment.scope_ack")
                    .put("schema_version", 2)
                    .put("device_id", settingsDeviceId())
                    .put("project_id", projectId)
                    .put("runtime_snapshot_id", snapshotId)
                    .put("assignment_epoch", epoch)
                    .put("connection_generation", generation)
                    .put("readiness", "READY")
                    .put("observed_state", StageCoreRuntimeBridge.assignedObservedState(projectId, snapshotId))
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
        synchronized (completedCommandIds) {
            if (completedCommandIds.contains(commandId)) return;
        }
        if (isPendingLiveCommand(commandId)) return;

        String projectId = command.optString("project_id", "");
        String snapshotId = command.optString("runtime_snapshot_id", "");
        if (!assignedProjectId.equals(projectId) || !assignedRuntimeSnapshotId.equals(snapshotId)) {
            throw new IllegalStateException("command scope differs from Hub assignment");
        }
        JSONObject payload = command.optJSONObject("payload");
        String manifestId = payload == null ? "" : payload.optString("tablet_manifest_id", "");
        String commandType = command.optString("command_type", "");
        main.post(() -> {
            CommandResult contentScope = StageCoreRuntimeBridge.validateV2ManifestHint(manifestId);
            if (contentScope.status != CommandStatus.COMPLETED) {
                remember(commandId);
                sendResult(webSocket, settingsDeviceId(), commandId, contentScope);
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
            remember(commandId);
            sendResult(webSocket, settingsDeviceId(), commandId, result);
            sendObservation(webSocket);
        });
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
                remember(commandId);
                sendResult(webSocket, settingsDeviceId(), commandId,
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
            remember(commandId);
            sendResult(webSocket, settingsDeviceId(), commandId, started);
            sendObservation(webSocket);
            return;
        }

        sendResult(webSocket, settingsDeviceId(), commandId, started);
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
        remember(commandId);
        sendResult(webSocket, settingsDeviceId(), commandId, terminal);
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
        synchronized (pendingLiveLock) {
            if (pendingLiveCommandSocket != webSocket) return;
            pendingLiveCommandId = "";
            pendingLiveCommandSocket = null;
            pendingLiveToken++;
        }
    }

    private void sendResult(WebSocket webSocket, String deviceId, String commandId, CommandResult result) {
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

    private void sendObservation(WebSocket webSocket) {
        try {
            boolean ready = runtimeAuthorityReady && "ACTIVE".equals(assignmentState);
            JSONObject json = new JSONObject()
                    .put("type", "device.observation")
                    .put("schema_version", 2)
                    .put("device_id", settingsDeviceId())
                    .put("readiness", ready ? "READY" : "BLOCKER")
                    .put("observed_state", ready
                            ? StageCoreRuntimeBridge.assignedObservedState(
                                    assignedProjectId, assignedRuntimeSnapshotId)
                            : StageCoreRuntimeBridge.inventoryObservedState())
                    .put("network_state", new JSONObject().put("transport", "WSS"));
            webSocket.send(json.toString());
        } catch (Exception ignored) {}
    }

    private String settingsDeviceId() { return AppSettings.load(context).deviceId; }

    private void remember(String commandId) {
        synchronized (completedCommandIds) {
            completedCommandIds.add(commandId);
            while (completedCommandIds.size() > 128) {
                String first = completedCommandIds.iterator().next();
                completedCommandIds.remove(first);
            }
        }
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
