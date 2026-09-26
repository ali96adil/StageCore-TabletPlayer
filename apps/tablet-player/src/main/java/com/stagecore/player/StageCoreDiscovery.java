package com.stagecore.player;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StageCoreDiscovery {
    public interface Callback {
        void onFound(StageCoreHubCandidate candidate);
        void onStatus(String message);
    }

    public static final String[] SERVICE_TYPES = new String[] {
            StageCoreHubCandidate.SERVICE_TYPE
    };

    private final NsdManager nsdManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final java.util.List<NsdManager.DiscoveryListener> listeners = new java.util.ArrayList<>();
    private volatile boolean running = false;

    public StageCoreDiscovery(Context context) {
        Object service = context.getSystemService(Context.NSD_SERVICE);
        nsdManager = service instanceof NsdManager ? (NsdManager) service : null;
    }

    public void start(Callback callback) {
        stop();
        if (nsdManager == null) {
            status(callback, "خدمة الاكتشاف التلقائي غير متوفرة بهذا الجهاز");
            return;
        }
        running = true;
        for (String serviceType : SERVICE_TYPES) {
            NsdManager.DiscoveryListener listener = listenerFor(serviceType, callback);
            listeners.add(listener);
            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                status(callback, "تعذر بدء البحث عن " + serviceType + ": " + ex.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        if (nsdManager == null) return;
        for (NsdManager.DiscoveryListener listener : new java.util.ArrayList<>(listeners)) {
            try {
                nsdManager.stopServiceDiscovery(listener);
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // Listener may already be stopped by Android.
            }
        }
        listeners.clear();
    }

    private NsdManager.DiscoveryListener listenerFor(String serviceType, Callback callback) {
        return new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String regType) {
                status(callback, "جاري البحث الآمن عن StageCore عبر Bonjour");
            }

            @Override public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (!running) return;
                String type = serviceInfo.getServiceType();
                if (type == null || !type.equals(serviceType)) return;
                resolve(serviceInfo, callback);
            }

            @Override public void onServiceLost(NsdServiceInfo serviceInfo) {
                status(callback, "اختفى StageCore Hub: " + serviceInfo.getServiceName());
            }

            @Override public void onDiscoveryStopped(String serviceType) {
                status(callback, "توقف البحث التلقائي");
            }

            @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                status(callback, "فشل بدء البحث: " + serviceType + " code=" + errorCode);
            }

            @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                status(callback, "فشل إيقاف البحث: " + serviceType + " code=" + errorCode);
            }
        };
    }

    private void resolve(NsdServiceInfo serviceInfo, Callback callback) {
        try {
            nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                @Override public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    status(callback, "تعذر قراءة عنوان StageCore Hub: "
                            + serviceInfo.getServiceName() + " code=" + errorCode);
                }

                @Override public void onServiceResolved(NsdServiceInfo resolved) {
                    InetAddress host = resolved.getHost();
                    if (host == null) {
                        status(callback, "تم العثور على StageCore Hub بدون عنوان IP واضح");
                        return;
                    }
                    try {
                        StageCoreHubCandidate candidate = StageCoreHubCandidate.fromTxt(
                                decodeTxt(resolved.getAttributes()),
                                host.getHostAddress(),
                                resolved.getPort(),
                                resolved.getServiceType());
                        mainHandler.post(() -> callback.onFound(candidate));
                    } catch (IllegalArgumentException error) {
                        status(callback, "تم تجاهل إعلان StageCore غير صالح: " + error.getMessage());
                    }
                }
            });
        } catch (IllegalArgumentException | IllegalStateException ex) {
            status(callback, "تعذر حل عنوان StageCore Hub: " + ex.getMessage());
        }
    }

    static Map<String, String> decodeTxt(Map<String, byte[]> attributes) {
        Map<String, String> txt = new LinkedHashMap<>();
        if (attributes == null) return txt;
        for (Map.Entry<String, byte[]> entry : attributes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            txt.put(entry.getKey(), new String(entry.getValue(), StandardCharsets.UTF_8));
        }
        return txt;
    }

    private void status(Callback callback, String message) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onStatus(message));
    }
}
