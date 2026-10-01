package com.stagecore.player;

import android.os.BatteryManager;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TabletHealthObservationTest {
    @Test public void batteryPercentIsBoundedAndHandlesUnknownValues() {
        assertEquals(50, TabletHealthObservation.batteryPercent(5, 10));
        assertEquals(100, TabletHealthObservation.batteryPercent(150, 100));
        assertEquals(0, TabletHealthObservation.batteryPercent(0, 100));
        assertEquals(-1, TabletHealthObservation.batteryPercent(-1, 100));
        assertEquals(-1, TabletHealthObservation.batteryPercent(50, 0));
    }

    @Test public void chargingStatusIncludesChargingAndFullOnly() {
        assertTrue(TabletHealthObservation.isChargingStatus(BatteryManager.BATTERY_STATUS_CHARGING));
        assertTrue(TabletHealthObservation.isChargingStatus(BatteryManager.BATTERY_STATUS_FULL));
        assertFalse(TabletHealthObservation.isChargingStatus(BatteryManager.BATTERY_STATUS_DISCHARGING));
        assertFalse(TabletHealthObservation.isChargingStatus(BatteryManager.BATTERY_STATUS_NOT_CHARGING));
    }
}
