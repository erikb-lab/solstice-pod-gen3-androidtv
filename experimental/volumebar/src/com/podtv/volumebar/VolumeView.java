package com.podtv.volumebar;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Slim vertical volume pill: level number on top, fill track, speaker icon at the bottom. */
public class VolumeView extends View {
    static final int W = 72, H = 340;
    private static final float PAD = 14, ICON = 30, TEXT_H = 40;

    private final Paint mBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mIcon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Path mPath = new Path();

    private float mFrac;          // animated 0..1
    private int mLevel;
    private boolean mMuted;
    private ValueAnimator mAnim;

    public VolumeView(Context c) {
        super(c);
        mBg.setColor(0xE6161618);
        mTrack.setColor(0x33FFFFFF);
        mFill.setColor(0xFFFFFFFF);
        mText.setColor(0xFFFFFFFF);
        mText.setTextAlign(Paint.Align.CENTER);
        mText.setTextSize(26);
        mText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mIcon.setColor(0xFFFFFFFF);
        mStroke.setColor(0xFFFFFFFF);
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeWidth(3.5f);
        mStroke.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onMeasure(int w, int h) { setMeasuredDimension(W, H); }

    void set(int level, int max, boolean muted, boolean animate) {
        mLevel = level;
        mMuted = muted;
        float target = muted || max == 0 ? 0f : (float) level / max;
        if (mAnim != null) mAnim.cancel();
        if (!animate) { mFrac = target; invalidate(); return; }
        mAnim = ValueAnimator.ofFloat(mFrac, target);
        mAnim.setDuration(160);
        mAnim.setInterpolator(new DecelerateInterpolator());
        mAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { mFrac = (float) a.getAnimatedValue(); invalidate(); }
        });
        mAnim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        mRect.set(0, 0, w, h);
        c.drawRoundRect(mRect, w / 2, w / 2, mBg);

        // level number
        String t = mMuted ? "0" : String.valueOf(mLevel);
        c.drawText(t, w / 2, PAD + 28, mText);

        // track
        float tw = 8, tx = (w - tw) / 2;
        float top = PAD + TEXT_H, bottom = h - PAD - ICON - 18;
        mRect.set(tx, top, tx + tw, bottom);
        c.drawRoundRect(mRect, tw / 2, tw / 2, mTrack);
        float fh = (bottom - top) * mFrac;
        if (fh > 0) {
            mRect.set(tx, bottom - fh, tx + tw, bottom);
            c.drawRoundRect(mRect, tw / 2, tw / 2, mFill);
        }

        drawSpeaker(c, w / 2 - 4, h - PAD - ICON / 2 - 4);
    }

    private void drawSpeaker(Canvas c, float cx, float cy) {
        // body
        mPath.reset();
        mPath.moveTo(cx - 12, cy - 5);
        mPath.lineTo(cx - 6, cy - 5);
        mPath.lineTo(cx + 2, cy - 12);
        mPath.lineTo(cx + 2, cy + 12);
        mPath.lineTo(cx - 6, cy + 5);
        mPath.lineTo(cx - 12, cy + 5);
        mPath.close();
        c.drawPath(mPath, mIcon);
        if (mMuted || mFrac == 0f) {
            c.drawLine(cx + 7, cy - 6, cx + 17, cy + 6, mStroke);
            c.drawLine(cx + 17, cy - 6, cx + 7, cy + 6, mStroke);
            return;
        }
        mRect.set(cx - 4, cy - 8, cx + 10, cy + 8);
        c.drawArc(mRect, -50, 100, false, mStroke);
        if (mFrac > 0.5f) {
            mRect.set(cx - 6, cy - 14, cx + 16, cy + 14);
            c.drawArc(mRect, -50, 100, false, mStroke);
        }
    }
}
