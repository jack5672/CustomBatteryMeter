package xyz.paphonb.custombatterymeter.xposed;

import de.robv.android.xposed.XposedHelpers;

public class BatteryTracker {
    private Object mTracker;

    public void setTracker(Object tracker) {
        mTracker = tracker;
    }

    public boolean shouldIndicateCharging() {
        return (boolean) XposedHelpers.callMethod(mTracker, "shouldIndicateCharging");
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
