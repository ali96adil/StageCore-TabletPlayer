package com.stagecore.player;

import android.app.Application;

/** Owns the process-wide official StageCore device connection. */
public final class StageCoreApplication extends Application {
    private StageCoreDeviceConnection deviceConnection;

    @Override
    public void onCreate() {
        super.onCreate();
        deviceConnection = new StageCoreDeviceConnection(this);
        deviceConnection.start();
    }

    public StageCoreDeviceConnection deviceConnection() {
        return deviceConnection;
    }

    @Override
    public void onTerminate() {
        if (deviceConnection != null) deviceConnection.stop();
        super.onTerminate();
    }
}
