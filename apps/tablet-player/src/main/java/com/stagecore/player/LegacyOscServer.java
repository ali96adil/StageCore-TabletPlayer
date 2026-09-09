package com.stagecore.player;

import android.os.Handler;
import android.os.Looper;

import com.stagecore.player.model.CommandResult;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class LegacyOscServer {
    private final ManifestExecutor executor;
    private final TabletPlayer player;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private DatagramSocket socket;
    private Thread thread;
    private volatile boolean running;
    private String overrideLiveUrl;

    public LegacyOscServer(ManifestExecutor executor, TabletPlayer player) {
        this.executor = executor;
        this.player = player;
    }

    public void start(int port) {
        if (running) return;
        running = true;
        thread = new Thread(() -> loop(port), "LegacyOscServer");
        thread.start();
    }

    public void stop() {
        running = false;
        if (socket != null) socket.close();
    }

    private void loop(int port) {
        try {
            socket = new DatagramSocket(port);
            byte[] buffer = new byte[4096];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                OscMessage message = parse(packet.getData(), packet.getLength());
                mainHandler.post(() -> dispatch(message));
            }
        } catch (IOException ignored) {
            running = false;
        }
    }

    private void dispatch(OscMessage message) {
        if (message.address == null) return;
        CommandResult result;
        String a = message.address;
        int first = message.intArgs.isEmpty() ? 1 : message.intArgs.get(0);
        String firstString = message.stringArgs.isEmpty() ? null : message.stringArgs.get(0);

        if (a.equals("/theatre/all/identify") || a.equals("/theatre/player/identify")) {
            result = player.identify();
        } else if (a.equals("/theatre/all/black") || a.equals("/theatre/all/blackout") || a.equals("/theatre/player/blackout")) {
            result = player.blackout();
        } else if (a.equals("/theatre/player/blackout/clear")) {
            result = player.clearBlackout();
        } else if (a.equals("/theatre/all/play") || a.equals("/theatre/player/main/play")) {
            result = executor.playMain(first);
        } else if (a.equals("/theatre/player/main/prepare")) {
            result = executor.prepareMain(first);
        } else if (a.equals("/theatre/player/main/pause")) {
            result = player.pauseMain();
        } else if (a.equals("/theatre/all/stop") || a.equals("/theatre/player/main/stop")) {
            result = player.stopMain();
        } else if (a.equals("/theatre/all/overlay/play") || a.equals("/theatre/player/overlay/play")) {
            result = executor.playOverlay(first);
        } else if (a.equals("/theatre/player/overlay/hide")) {
            result = player.hideOverlay(first);
        } else if (a.equals("/theatre/player/live/url")) {
            overrideLiveUrl = firstString;
            result = overrideLiveUrl == null
                    ? CommandResult.failed("LIVE_URL_MISSING", "OSC string arg required")
                    : CommandResult.completed("Live URL set");
        } else if (a.equals("/theatre/all/live/show") || a.equals("/theatre/player/live/show")) {
            if (firstString != null) {
                result = player.showLive(firstString);
            } else if (overrideLiveUrl != null) {
                result = player.showLive(overrideLiveUrl);
            } else {
                result = executor.showLive("live.camera.01");
            }
        } else if (a.equals("/theatre/all/live/hide") || a.equals("/theatre/player/live/hide")) {
            result = player.hideLive();
        } else if (a.equals("/theatre/all/cue/go") || a.equals("/theatre/player/cue/go")) {
            result = executor.goCue(first);
        } else if (a.equals("/theatre/player/cue/go_id")) {
            result = executor.goCueById(firstString);
        } else if (a.equals("/theatre/all/cue/prepare") || a.equals("/theatre/player/cue/prepare")) {
            result = executor.prepareCue(first);
        } else if (a.equals("/theatre/player/cue/prepare_id")) {
            result = executor.prepareCueById(firstString);
        } else {
            result = CommandResult.rejected("UNKNOWN_OSC", "Unknown OSC address " + a);
        }
        android.util.Log.i("StageCorePlayer", "OSC " + a + " -> " + result);
    }

    private OscMessage parse(byte[] data, int length) {
        String address = readPaddedString(data, 0, length);
        int offset = paddedLength(address);
        String typeTags = offset < length ? readPaddedString(data, offset, length) : "";
        offset += paddedLength(typeTags);
        List<Integer> ints = new ArrayList<>();
        List<String> strings = new ArrayList<>();
        if (typeTags != null && typeTags.startsWith(",")) {
            for (int i = 1; i < typeTags.length() && offset < length; i++) {
                char tag = typeTags.charAt(i);
                if (tag == 'i' && offset + 4 <= length) {
                    ints.add(ByteBuffer.wrap(data, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt());
                    offset += 4;
                } else if (tag == 'f' && offset + 4 <= length) {
                    offset += 4;
                } else if (tag == 's') {
                    String value = readPaddedString(data, offset, length);
                    strings.add(value);
                    offset += paddedLength(value);
                }
            }
        }
        return new OscMessage(address, ints, strings);
    }

    private String readPaddedString(byte[] data, int offset, int length) {
        int end = offset;
        while (end < length && data[end] != 0) end++;
        return new String(data, offset, Math.max(0, end - offset), StandardCharsets.UTF_8);
    }

    private int paddedLength(String value) {
        int raw = value == null ? 1 : value.getBytes(StandardCharsets.UTF_8).length + 1;
        while (raw % 4 != 0) raw++;
        return raw;
    }

    private static final class OscMessage {
        final String address;
        final List<Integer> intArgs;
        final List<String> stringArgs;
        OscMessage(String address, List<Integer> intArgs, List<String> stringArgs) {
            this.address = address;
            this.intArgs = intArgs;
            this.stringArgs = stringArgs;
        }
    }
}
