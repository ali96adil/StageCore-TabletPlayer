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
import java.util.concurrent.atomic.AtomicLong;

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
    private final AtomicLong discoveryGeneration = new AtomicLong();
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
        final long generation = discoveryGeneration.incrementAndGet();
        running = true;
        for (String serviceType : SERVICE_TYPES) {
            NsdManager.DiscoveryListener listener = listenerFor(serviceType, callback, generation);
            listeners.add(listener);
            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                statusIfCurrent(
                        callback,
                        generation,
                        "تعذر بدء البحث عن " + serviceType + ": " + ex.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        discoveryGeneration.incrementAndGet();
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

    private NsdManager.DiscoveryListener listenerFor(
            String serviceType,
            Callback callback,
            long generation) {
        return new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String regType) {
                statusIfCurrent(callback, generation, "جاري البحث الآمن عن StageCore عبر Bonjour");
            }

            @Override public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (!isCurrent(generation)) return;
                String type = serviceInfo.getServiceType();
                if (!StageCoreHubCandidate.isSupportedServiceType(type)) return;
                // Android NSD may normalize the service type again on the resolved
                // NsdServiceInfo. Carry forward the already validated discovery type
                // rather than treating that platform formatting change as new trust input.
                resolve(serviceInfo, type, callback, generation);
            }

            @Override public void onServiceLost(NsdServiceInfo serviceInfo) {
                statusIfCurrent(
                        callback,
                        generation,
                        "اختفى StageCore Hub: " + serviceInfo.getServiceName());
            }

            @Override public void onDiscoveryStopped(String serviceType) {
                statusIfCurrent(callback, generation, "توقف البحث التلقائي");
            }

            @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                statusIfCurrent(
                        callback,
                        generation,
                        "فشل بدء البحث: " + serviceType + " code=" + errorCode);
            }

            @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                statusIfCurrent(
                        callback,
                        generation,
                        "فشل إيقاف البحث: " + serviceType + " code=" + errorCode);
            }
        };
    }

    private void resolve(
            NsdServiceInfo serviceInfo,
            String validatedServiceType,
            Callback callback,
            long generation) {
        try {
            nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                @Override public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    statusIfCurrent(
                            callback,
                            generation,
                            "تعذر قراءة عنوان StageCore Hub: "
                                    + serviceInfo.getServiceName() + " code=" + errorCode);
                }

                @Override public void onServiceResolved(NsdServiceInfo resolved) {
                    if (!isCurrent(generation)) return;
                    InetAddress host = resolved.getHost();
                    if (host == null) {
                        statusIfCurrent(
                                callback,
                                generation,
                                "تم العثور على StageCore Hub بدون عنوان IP واضح");
                        return;
                    }
                    try {
                        StageCoreHubCandidate candidate = StageCoreHubCandidate.fromTxt(
                                decodeTxt(resolved.getAttributes()),
                                host.getHostAddress(),
                                resolved.getPort(),
                                validatedServiceType);
                        mainHandler.post(() -> {
                            if (isCurrent(generation)) callback.onFound(candidate);
                        });
                    } catch (IllegalArgumentException error) {
                        statusIfCurrent(
                                callback,
                                generation,
                                "تم تجاهل إعلان StageCore غير صالح: " + error.getMessage());
                    }
                }
            });
        } catch (IllegalArgumentException | IllegalStateException ex) {
            statusIfCurrent(
                    callback,
                    generation,
                    "تعذر حل عنوان StageCore Hub: " + ex.getMessage());
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

    private boolean isCurrent(long generation) {
        return running && discoveryGeneration.get() == generation;
    }

    private void statusIfCurrent(Callback callback, long generation, String message) {
        if (callback == null || !isCurrent(generation)) return;
        mainHandler.post(() -> {
            if (isCurrent(generation)) callback.onStatus(message);
        });
    }

    private void status(Callback callback, String message) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onStatus(message));
    }
}
