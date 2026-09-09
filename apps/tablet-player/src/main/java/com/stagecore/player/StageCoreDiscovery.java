package com.stagecore.player;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public final class StageCoreDiscovery {
    public interface Callback {
        void onFound(String name, String host, int port, String serviceType);
        void onStatus(String message);
    }

    public static final String[] SERVICE_TYPES = new String[] {
            "_stagecore._tcp.",
            "_stagecore-hub._tcp."
    };

    private final NsdManager nsdManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<NsdManager.DiscoveryListener> listeners = new ArrayList<>();
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
        for (NsdManager.DiscoveryListener listener : new ArrayList<>(listeners)) {
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
            @Override
            public void onDiscoveryStarted(String regType) {
                status(callback, "جاري البحث عن StageCore عبر Bonjour: " + regType);
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (!running) return;
                String type = serviceInfo.getServiceType();
                if (type == null || !type.equals(serviceType)) return;
                resolve(serviceInfo, callback);
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                status(callback, "اختفى سيرفر StageCore: " + serviceInfo.getServiceName());
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                status(callback, "توقف البحث التلقائي");
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                status(callback, "فشل بدء البحث: " + serviceType + " code=" + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                status(callback, "فشل إيقاف البحث: " + serviceType + " code=" + errorCode);
            }
        };
    }

    private void resolve(NsdServiceInfo serviceInfo, Callback callback) {
        try {
            nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    status(callback, "تعذر قراءة عنوان السيرفر: " + serviceInfo.getServiceName() + " code=" + errorCode);
                }

                @Override
                public void onServiceResolved(NsdServiceInfo resolved) {
                    InetAddress host = resolved.getHost();
                    if (host == null) {
                        status(callback, "تم العثور على StageCore بدون عنوان IP واضح");
                        return;
                    }
                    mainHandler.post(() -> callback.onFound(
                            resolved.getServiceName(),
                            host.getHostAddress(),
                            resolved.getPort(),
                            resolved.getServiceType()
                    ));
                }
            });
        } catch (IllegalArgumentException | IllegalStateException ex) {
            status(callback, "تعذر حل عنوان StageCore: " + ex.getMessage());
        }
    }

    private void status(Callback callback, String message) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onStatus(message));
    }
}
