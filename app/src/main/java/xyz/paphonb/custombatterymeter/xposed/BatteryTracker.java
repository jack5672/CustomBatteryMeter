package xyz.paphonb.custombatterymeter.xposed;

import android.os.BatteryManager;

import de.robv.android.xposed.XposedHelpers;

public class BatteryTracker {
    private Object mTracker;

    public void setTracker(Object tracker) {
        mTracker = tracker;
    }

    public boolean shouldIndicateCharging() {
        return status() == BatteryManager.BATTERY_STATUS_CHARGING || plugged() && status() == BatteryManager.BATTERY_STATUS_FULL;
    }

    public int level() {
        return XposedHelpers.getIntField(mTracker, "level");
    }

    public int status() {
        return XposedHelpers.getIntField(mTracker, "status");
    }

    public boolean plugged() {
        return XposedHelpers.getBooleanField(mTracker, "plugged");
    }
}
