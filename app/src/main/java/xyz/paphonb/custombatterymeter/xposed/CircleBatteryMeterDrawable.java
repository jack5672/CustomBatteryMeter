package xyz.paphonb.custombatterymeter.xposed;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;

import static xyz.paphonb.custombatterymeter.xposed.CustomBatteryMeterXposed.PACKAGE_SYSTEMUI;

public class CircleBatteryMeterDrawable extends BatteryMeterDrawable {
    private static final boolean SINGLE_DIGIT_PERCENT = false;
    private static final boolean SHOW_100_PERCENT = false;

    private static final int FULL = 96;

    public static final float STROKE_WITH = 6.5f;

    private boolean mDisposed;

    private int mAnimOffset;
    private boolean mIsAnimating;   // stores charge-animation status to reliably
    //remove callbacks

    private int mCircleSize;    // draw size of circle
    private RectF mRectLeft;      // contains the precalculated rect used in drawArc(),
    // derived from mCircleSize
    private float mTextX, mTextY; // precalculated position for drawText() to appear centered

    private Paint mTextPaint;
    private Paint mFrontPaint;
    private Paint mBackPaint;
    private Paint mWarningTextPaint;

    private final RectF mBoltFrame = new RectF();

    private final float[] mBoltPoints;
    private final Path mBoltPath = new Path();

    @SuppressWarnings("deprecation")
    public CircleBatteryMeterDrawable(Resources res) {
        super();
        mDisposed = false;

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Typeface font = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        mTextPaint.setTypeface(font);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        mFrontPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFrontPaint.setStrokeCap(Paint.Cap.BUTT);
        mFrontPaint.setDither(true);
        mFrontPaint.setStrokeWidth(0);

        mBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBackPaint.setColor(res.getColor(res.getIdentifier("batterymeter_frame_color", "color", PACKAGE_SYSTEMUI)));
        mBackPaint.setStrokeCap(Paint.Cap.BUTT);
        mBackPaint.setDither(true);
        mBackPaint.setStrokeWidth(0);

        mBoltPoints = loadBoltPoints(res);
    }

    private float[] loadBoltPoints(Resources res) {
        final int[] pts = res.getIntArray(res.getIdentifier("batterymeter_bolt_points", "array", PACKAGE_SYSTEMUI));
        int maxX = 0, maxY = 0;
        for (int i = 0; i < pts.length; i += 2) {
            maxX = Math.max(maxX, pts[i]);
            maxY = Math.max(maxY, pts[i + 1]);
        }
        final float[] ptsF = new float[pts.length];
        for (int i = 0; i < pts.length; i += 2) {
            ptsF[i] = (float)pts[i] / maxX;
            ptsF[i + 1] = (float)pts[i + 1] / maxY;
        }
        return ptsF;
    }

    @Override
    public void onDraw(Canvas c, BatteryTracker tracker) {
        if (mDisposed) return;

        if (mRectLeft == null) {
            initSizeBasedStuff();
        }

        drawCircle(c, tracker, mTextX);
        updateChargeAnim(tracker);
    }

    @Override
    public void onDispose() {
        mDisposed = true;
    }

    @Override
    public void setDarkIntensity(int backgroundColor, int fillColor) {
        invalidate();
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        initSizeBasedStuff();
    }

    private void drawCircle(Canvas canvas, BatteryTracker tracker, float textX) {
        int status = tracker.status();
        boolean unknownStatus = status == BatteryManager.BATTERY_STATUS_UNKNOWN;
        int level = tracker.level();
        Paint paint;

        mCircleSize = Math.min(getMeasuredWidth(), getMeasuredHeight());

        int pLeft = getPaddingLeft();
        int drawSize = mCircleSize - (pLeft * 2);
        int drawRadius = drawSize / 2;

        if (unknownStatus) {
            paint = mBackPaint;
            level = 100; // Draw all the circle;
        } else {
            paint = mFrontPaint;
            paint.setColor(getColorForLevel(level));
            if (status == BatteryManager.BATTERY_STATUS_FULL) {
                level = 100;
            }
        }

        canvas.drawCircle(drawRadius, drawRadius, drawRadius, mBackPaint);
        if (level != 0) {
            int alpha = Math.min(Math.abs(mAnimOffset - 255), 255);
            paint.setAlpha(alpha);
            //Log.d(TAG, "alpha: " + alpha);
            float drawProgress = drawRadius * ((float) level / (float) 100);
            canvas.drawCircle(drawRadius, drawRadius, drawProgress, paint);
        }
        if (unknownStatus) {
            mTextPaint.setColor(paint.getColor());
            canvas.drawText("?", textX, mTextY, mTextPaint);

        } else {
            if (mShowPercent && level != 100) {
                String pctText = String.valueOf(level);
                canvas.drawText(pctText, textX, mTextY, mTextPaint);
            }
        }
    }

    private void updateChargeAnim(BatteryTracker tracker) {
        // Stop animation when battery is full or after the meter
        // faded back to 255 after unplugging.
        mIsAnimating = !(!tracker.shouldIndicateCharging()
                || tracker.status() == BatteryManager.BATTERY_STATUS_FULL
                || tracker.level() == 0);

        if (mAnimOffset > 511) {
            mAnimOffset = 0;
        }

        boolean continueAnimation = mIsAnimating || mAnimOffset != 0;

        if (continueAnimation) {
            mAnimOffset += 24;
        }

        if (continueAnimation) {
            postInvalidateDelayed(50);
        }
    }

    private void initSizeBasedStuff() {
        mCircleSize = Math.min(getMeasuredWidth(), getMeasuredHeight());
        mTextPaint.setTextSize(mCircleSize / 2f);
        //mWarningTextPaint.setTextSize(mCircleSize / 2f);

        float strokeWidth = mCircleSize / STROKE_WITH;

        // calculate rectangle for drawArc calls
        int pLeft = getPaddingLeft();
        mRectLeft = new RectF(pLeft + strokeWidth / 2.0f, 0 + strokeWidth / 2.0f, mCircleSize
                - strokeWidth / 2.0f + pLeft, mCircleSize - strokeWidth / 2.0f);

        // calculate Y position for text
        Rect bounds = new Rect();
        mTextPaint.getTextBounds("99", 0, "99".length(), bounds);
        mTextX = mCircleSize / 2.0f + getPaddingLeft();
        // the +1dp at end of formula balances out rounding issues.works out on all resolutions
        mTextY = mCircleSize / 2.0f + (bounds.bottom - bounds.top) / 2.0f
                - strokeWidth / 2.0f + getResources().getDisplayMetrics().density;

        // draw the bolt
        final float bl = (int) (mRectLeft.left + mRectLeft.width() / 3.2f);
        final float bt = (int) (mRectLeft.top + mRectLeft.height() / 4f);
        final float br = (int) (mRectLeft.right - mRectLeft.width() / 5.2f);
        final float bb = (int) (mRectLeft.bottom - mRectLeft.height() / 8f);
        if (mBoltFrame.left != bl || mBoltFrame.top != bt
                || mBoltFrame.right != br || mBoltFrame.bottom != bb) {
            mBoltFrame.set(bl, bt, br, bb);
            mBoltPath.reset();
            mBoltPath.moveTo(
                    mBoltFrame.left + mBoltPoints[0] * mBoltFrame.width(),
                    mBoltFrame.top + mBoltPoints[1] * mBoltFrame.height());
            for (int i = 2; i < mBoltPoints.length; i += 2) {
                mBoltPath.lineTo(
                        mBoltFrame.left + mBoltPoints[i] * mBoltFrame.width(),
                        mBoltFrame.top + mBoltPoints[i + 1] * mBoltFrame.height());
            }
            mBoltPath.lineTo(
                    mBoltFrame.left + mBoltPoints[0] * mBoltFrame.width(),
                    mBoltFrame.top + mBoltPoints[1] * mBoltFrame.height());
        }
    }
}