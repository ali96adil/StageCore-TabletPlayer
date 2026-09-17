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
 * results/observations using stagecore.device/1.
 */
public final class StageCoreDeviceConnection {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final OkHttpClient websocketClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(10, TimeUnit.SECONDS)
            .build();
    private final Set<String> completedCommandIds = new LinkedHashSet<>();

    private volatile boolean stopped;
    private volatile WebSocket socket;
    private volatile String lastStatus = "IDLE";
    private volatile String pendingPairingCode = "";

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

    private void connectionLoop() {
        long backoffMs = 1000;
        while (!stopped) {
            try {
                if (!StageCoreRuntimeBridge.isReady()) {
                    sleep(500);
                    continue;
                }
                AppSettings settings = AppSettings.load(context);
                if (settings.serverHost == null || settings.serverHost.trim().isEmpty()) {
                    lastStatus = "WAITING_FOR_SERVER";
                    sleep(1500);
                    continue;
                }
                String baseUrl = secureBaseUrl(settings);
                StageCoreClient descriptor = new StageCoreClient(settings.deviceId, settings.deviceName);
                StageCorePairingClient pairing = new StageCorePairingClient(settings.deviceId, settings.deviceName);
                StageCorePairingClient.Session session;
                try {
                    session = pairing.authenticate(baseUrl);
                } catch (StageCorePairingClient.StageCoreAuthException authError) {
                    if (!"COMPANION_UNPAIRED".equals(authError.errorCode)) throw authError;
                    lastStatus = "PAIRING_REQUIRED";
                    StageCorePairingClient.PairingReceipt receipt = pairing.requestPairing(baseUrl, descriptor.baselineCapabilities());
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
                connectWebSocket(baseUrl, settings, descriptor, session);
                backoffMs = 1000;
                while (!stopped && socket != null) sleep(500);
            } catch (Throwable error) {
                lastStatus = "ERROR:" + error.getClass().getSimpleName();
                sleep(backoffMs);
                backoffMs = Math.min(15000, backoffMs * 2);
            }
        }
    }

    private void connectWebSocket(String baseUrl, AppSettings settings, StageCoreClient descriptor, StageCorePairingClient.Session session) throws Exception {
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
                lastStatus = "DISCONNECTED";
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (socket == webSocket) socket = null;
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
            json.put("project_id", StageCoreRuntimeBridge.projectId());
            json.put("profile_id", "stagecore.tablet-player");
            json.put("device_kind", "TABLET_PLAYER");
            json.put("display_name", settings.deviceName);
            json.put("platform", "android");
            json.put("architecture", Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0]);
            json.put("client_version", BuildConfig.VERSION_NAME);
            json.put("protocol_version", StageCoreClient.PROTOCOL);
            json.put("capabilities", new JSONArray(descriptor.baselineCapabilities()));
            json.put("readiness", "READY");
            json.put("observed_state", StageCoreRuntimeBridge.observedState());
            json.put("network_state", new JSONObject().put("transport", "WSS"));
        } catch (Exception ignored) {}
        return json;
    }

    private void handleMessage(WebSocket webSocket, String raw) {
        try {
            JSONObject message = new JSONObject(raw);
            String type = message.optString("type", "");
            if ("runtime.ready".equals(type)) {
                lastStatus = "READY";
                sendObservation(webSocket);
                return;
            }
            if ("display.state".equals(type)) {
                return; // tablet media player has no Stage Display surface.
            }
            if (!"command.execute".equals(type)) return;
            JSONObject command = message.getJSONObject("command");
            String commandId = command.getString("command_id");
            synchronized (completedCommandIds) {
                if (completedCommandIds.contains(commandId)) return;
            }
            String projectId = command.optString("project_id", "");
            String snapshotId = command.optString("runtime_snapshot_id", "");
            JSONObject payload = command.optJSONObject("payload");
            String manifestId = payload == null ? "" : payload.optString("tablet_manifest_id", "");
            main.post(() -> {
                CommandResult scope = StageCoreRuntimeBridge.validateScope(projectId, snapshotId, manifestId);
                CommandResult result = scope.status == CommandStatus.COMPLETED
                        ? StageCoreRuntimeBridge.execute(command.optString("command_type", ""), payload)
                        : scope;
                remember(commandId);
                sendResult(webSocket, settingsDeviceId(), commandId, result);
                sendObservation(webSocket);
            });
        } catch (Exception ignored) {
            lastStatus = "PROTOCOL_ERROR";
        }
    }

    private void sendResult(WebSocket webSocket, String deviceId, String commandId, CommandResult result) {
        try {
            JSONObject json = new JSONObject()
                    .put("type", "command.result")
                    .put("schema_version", 1)
                    .put("device_id", deviceId)
                    .put("command_id", commandId)
                    .put("status", result.status.toString())
                    .put("payload", new JSONObject().put("message", result.message));
            if (!"COMPLETED".equals(result.status.toString())) {
                json.put("error", new JSONObject()
                        .put("error_code", result.code)
                        .put("category", "DEVICE")
                        .put("message", result.message)
                        .put("retryable", false));
            }
            webSocket.send(json.toString());
        } catch (Exception ignored) {}
    }

    private void sendObservation(WebSocket webSocket) {
        try {
            JSONObject json = new JSONObject()
                    .put("type", "device.observation")
                    .put("schema_version", 1)
                    .put("device_id", settingsDeviceId())
                    .put("readiness", StageCoreRuntimeBridge.isReady() ? "READY" : "BLOCKER")
                    .put("observed_state", StageCoreRuntimeBridge.observedState())
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
