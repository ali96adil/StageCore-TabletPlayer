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
            byte[] buffer = new byte[2048];
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

        if (a.equals("/theatre/all/identify") || a.equals("/theatre/player/identify")) {
            result = player.identify();
        } else if (a.equals("/theatre/all/black") || a.equals("/theatre/all/blackout") || a.equals("/theatre/player/blackout")) {
            result = player.blackout();
        } else if (a.equals("/theatre/all/play") || a.equals("/theatre/player/main/play")) {
            result = executor.playMain(first);
        } else if (a.equals("/theatre/all/overlay/play") || a.equals("/theatre/player/overlay/play")) {
            result = executor.playOverlay(first);
        } else if (a.equals("/theatre/all/live/show") || a.equals("/theatre/player/live/show")) {
            result = executor.showLive("live.camera.01");
        } else if (a.equals("/theatre/all/live/hide") || a.equals("/theatre/player/live/hide")) {
            result = player.hideLive();
        } else if (a.equals("/theatre/all/cue/go") || a.equals("/theatre/player/cue/go")) {
            result = executor.goCue(first);
        } else if (a.equals("/theatre/all/cue/prepare") || a.equals("/theatre/player/cue/prepare")) {
            result = executor.prepareCue(first);
        } else {
            result = CommandResult.rejected("UNKNOWN_OSC", "Unknown OSC address " + a);
        }
        // Keep the current MVP local-only. Command result transport is added in StageCoreClient.
        android.util.Log.i("StageCorePlayer", "OSC " + a + " -> " + result);
    }

    private OscMessage parse(byte[] data, int length) {
        String address = readPaddedString(data, 0, length);
        int offset = paddedLength(address);
        String typeTags = offset < length ? readPaddedString(data, offset, length) : "";
        offset += paddedLength(typeTags);
        List<Integer> ints = new ArrayList<>();
        if (typeTags != null && typeTags.startsWith(",")) {
            for (int i = 1; i < typeTags.length() && offset + 4 <= length; i++) {
                if (typeTags.charAt(i) == 'i') {
                    ints.add(ByteBuffer.wrap(data, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt());
                    offset += 4;
                } else if (typeTags.charAt(i) == 'f') {
                    offset += 4;
                } else if (typeTags.charAt(i) == 's') {
                    String ignored = readPaddedString(data, offset, length);
                    offset += paddedLength(ignored);
                }
            }
        }
        return new OscMessage(address, ints);
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
        OscMessage(String address, List<Integer> intArgs) {
            this.address = address;
            this.intArgs = intArgs;
        }
    }
}
