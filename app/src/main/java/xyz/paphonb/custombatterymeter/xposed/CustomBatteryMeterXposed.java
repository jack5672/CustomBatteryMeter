package xyz.paphonb.custombatterymeter.xposed;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.view.View;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class CustomBatteryMeterXposed implements IXposedHookLoadPackage {
    public static final String PACKAGE_SYSTEMUI = "com.android.systemui";
    private static final String BATTERY_METER_DRAWABLE = "BatteryMeterDrawable";
    private static final String BATTERY_TRACKER = "BatteryTracker";
    private static final String LOG_FORMAT = "%1$sCustomBatteryMeter %2$s: %3$s";
    private static final String TAG = "CustomBatteryMeterXposed";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(PACKAGE_SYSTEMUI)) return;

        try {
            XposedHelpers.findClass("com.android.systemui.BatteryMeterView$BatteryMeterDrawable", lpparam.classLoader);
            initCm(lpparam);
        } catch (Throwable t) {
            initAosp(lpparam);
        }
    }

    private void initAosp(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            final Class<?> classBatteryMeterView = XposedHelpers.findClass("com.android.systemui.BatteryMeterView", lpparam.classLoader);

            XposedBridge.hookAllConstructors(classBatteryMeterView, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = (Context) param.args[0];
                    Resources res = context.getResources();
                    createBatteryMeterDrawable(res, param.thisObject);
                }
            });

            hookOnDraw(classBatteryMeterView, "draw");
            hookOnSizeChanged(classBatteryMeterView);
            hookSetDarkIntensity(classBatteryMeterView);

            XposedHelpers.findAndHookMethod(View.class, "onMeasure", int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject.getClass().getName().equals(classBatteryMeterView.getName())) {
                        replaceOnMeasure(param);
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            logE(TAG, "Error in initAosp", t);
        }
    }

    private void initCm(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> classBatteryMeterView = XposedHelpers.findClass("com.android.systemui.BatteryMeterView", lpparam.classLoader);

            XposedBridge.hookAllMethods(classBatteryMeterView, "createBatteryMeterDrawable", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    Resources res = context.getResources();
                    createBatteryMeterDrawable(res, param.thisObject);
                }
            });

            hookOnDraw(classBatteryMeterView, "onDraw");
            hookOnSizeChanged(classBatteryMeterView);
            hookSetDarkIntensity(classBatteryMeterView);

            XposedHelpers.findAndHookMethod(classBatteryMeterView, "onMeasure", int.class, int.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    replaceOnMeasure(param);
                    return null;
                }
            });
        } catch (Throwable t) {
            logE(TAG, "Error in initCm", t);
        }
    }

    private void replaceOnMeasure(XC_MethodHook.MethodHookParam param) {
        int height = View.MeasureSpec.getSize((Integer) param.args[1]);
        height += (CircleBatteryMeterDrawable.STROKE_WITH / 3);
        XposedHelpers.callMethod(param.thisObject, "setMeasuredDimension", height, height);
    }

    private void hookSetDarkIntensity(Class<?> classBatteryMeterView) {
        XposedHelpers.findAndHookMethod(classBatteryMeterView, "setDarkIntensity", float.class, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                float darkIntensity = (float) param.args[0];
                int backgroundColor = (int) XposedHelpers.callMethod(param.thisObject, "getBackgroundColor", darkIntensity);
                int fillColor = (int) XposedHelpers.callMethod(param.thisObject, "getFillColor", darkIntensity);
                XposedHelpers.setIntField(param.thisObject, "mIconTint", fillColor);
                BatteryMeterDrawable batteryMeterDrawable = getBatteryMeterDrawable(param.thisObject);
                if (batteryMeterDrawable != null) {
                    batteryMeterDrawable.setDarkIntensity(backgroundColor, fillColor);
                }
                return null;
            }
        });
    }

    private void hookOnSizeChanged(Class<?> classBatteryMeterView) {
        XposedHelpers.findAndHookMethod(classBatteryMeterView, "onSizeChanged", int.class, int.class, int.class, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                BatteryMeterDrawable batteryMeterDrawable = getBatteryMeterDrawable(param.thisObject);
                if (batteryMeterDrawable != null) {
                    batteryMeterDrawable.onSizeChanged((int) param.args[0], (int) param.args[1], (int) param.args[2], (int) param.args[3]);
                }
            }
        });
    }

    private void hookOnDraw(Class<?> classBatteryMeterView, String methodName) {
        XposedHelpers.findAndHookMethod(classBatteryMeterView, methodName, Canvas.class, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                BatteryMeterDrawable batteryMeterDrawable = getBatteryMeterDrawable(param.thisObject);
                if (batteryMeterDrawable != null) {
                    Canvas canvas = (Canvas) param.args[0];
                    onDraw(canvas, param.thisObject);
                }
                return null;
            }
        });
    }

    private void onDraw(Canvas canvas, Object thisObject) {
        BatteryTracker batteryTracker = getTracker(thisObject);
        if (batteryTracker == null) {
            batteryTracker = new BatteryTracker();
            setTracker(thisObject, batteryTracker);
        }
        boolean mDemoMode = XposedHelpers.getBooleanField(thisObject, "mDemoMode");
        batteryTracker.setTracker(XposedHelpers.getObjectField(thisObject, mDemoMode ? "mDemoTracker" : "mTracker"));
        BatteryMeterDrawable batteryMeterDrawable = getBatteryMeterDrawable(thisObject);
        if (batteryMeterDrawable != null) {
            batteryMeterDrawable.onDraw(canvas, batteryTracker);
        }
    }

    private void createBatteryMeterDrawable(Resources res, Object thisObject) {
        BatteryMeterDrawable batteryMeterDrawable = new CircleBatteryMeterDrawable(res);
        batteryMeterDrawable.setBatteryMeterView((View) thisObject);
        setBatteryMeterDrawable(thisObject, batteryMeterDrawable);
    }

    private void setBatteryMeterDrawable(Object thisObject, BatteryMeterDrawable batteryMeterDrawable) {
        XposedHelpers.setAdditionalInstanceField(thisObject, BATTERY_METER_DRAWABLE, batteryMeterDrawable);
    }

    private BatteryMeterDrawable getBatteryMeterDrawable(Object thisObject) {
        Object batteryMeterDrawable = XposedHelpers.getAdditionalInstanceField(thisObject, BATTERY_METER_DRAWABLE);
        if (batteryMeterDrawable != null && batteryMeterDrawable instanceof BatteryMeterDrawable) {
            return (BatteryMeterDrawable) batteryMeterDrawable;
        } else {
            return null;
        }
    }

    private void setTracker(Object thisObject, BatteryTracker batteryTracker) {
        XposedHelpers.setAdditionalInstanceField(thisObject, BATTERY_TRACKER, batteryTracker);
    }

    private BatteryTracker getTracker(Object thisObject) {
        Object tracker = XposedHelpers.getAdditionalInstanceField(thisObject, BATTERY_TRACKER);
        if (tracker != null && tracker instanceof BatteryTracker) {
            return (BatteryTracker) tracker;
        } else {
            return null;
        }
    }

    public static void logE(String tag, String msg, Throwable t) {
        XposedBridge.log(String.format(LOG_FORMAT, "E/", tag, msg));
        if (t != null)
            XposedBridge.log(t);
    }
}
