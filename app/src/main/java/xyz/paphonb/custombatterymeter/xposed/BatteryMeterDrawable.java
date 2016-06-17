package xyz.paphonb.custombatterymeter.xposed;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.view.View;

import de.robv.android.xposed.XposedHelpers;

public abstract class BatteryMeterDrawable {
    protected View mBatteryMeterView;

    public void setBatteryMeterView(View batteryMeterView) {
        mBatteryMeterView = batteryMeterView;
    }

    protected void invalidate() {
        mBatteryMeterView.invalidate();
    }

    public int getColorForLevel(int percent) {
        return (int) XposedHelpers.callMethod(mBatteryMeterView, "getColorForLevel", percent);
    }

    public void postInvalidateDelayed(long delayMilliseconds) {
        mBatteryMeterView.postInvalidateDelayed(delayMilliseconds);
    }

    public int getMeasuredWidth() {
        return mBatteryMeterView.getMeasuredWidth();
    }

    public int getMeasuredHeight() {
        return mBatteryMeterView.getMeasuredHeight();
    }

    public int getPaddingLeft() {
        return mBatteryMeterView.getPaddingLeft();
    }

    public Resources getResources() {
        return mBatteryMeterView.getResources();
    }

    public abstract void onSizeChanged(int w, int h, int oldw, int oldh);

    public abstract void onDraw(Canvas c, BatteryTracker tracker);

    public abstract void onDispose();

    public abstract void setDarkIntensity(int backgroundColor, int fillColor);
}
