package org.solovyev.android.calculator.autoclicker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Draws a HOLLOW reticle for the autoclicker overlay:
 *   - a thin stroked ring (the circle boundary)
 *   - a small solid dot at the exact center for aiming
 * The interior is NEVER filled, so the center point is always clearly visible.
 *
 * Drawn programmatically (Canvas) instead of via a <shape> drawable, because on some
 * devices/ROMs an oval shape without <solid> still rendered as a filled disc. Canvas
 * stroke vs fill is unambiguous.
 */
public class AutoClickerReticle extends View {

    private int ringColor = 0xFFFF0000;   // default red
    private int dotColor = 0xFFFF0000;
    private float density;

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Sizes in dp (converted to px in constructor / onSizeChanged).
    private static final float RING_WIDTH_DP = 1.5f;  // thin edge
    private static final float DOT_RADIUS_DP = 3.0f;  // visible center pixel

    public AutoClickerReticle(Context context) {
        super(context);
        init(context);
    }

    public AutoClickerReticle(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AutoClickerReticle(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(RING_WIDTH_DP * density);
        ringPaint.setColor(ringColor);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(dotColor);
        // CRITICAL: remove ALL background — EMUI themes inject opaque backgrounds that
        // fill the interior even when onDraw only strokes a ring.
        setBackground(null);
        // Force software layer to guarantee transparency works (some HW compositors
        // treat overlay windows as opaque).
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    /** Set the ring + center-dot color (e.g. red for circle 0, blue for circle 1). */
    public void setColors(int ring, int dot) {
        this.ringColor = ring;
        this.dotColor = dot;
        ringPaint.setColor(ring);
        dotPaint.setColor(dot);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) return;
        final float cx = w / 2f;
        final float cy = h / 2f;
        final float ringRadius = Math.min(w, h) / 2f - ringPaint.getStrokeWidth() / 2f;
        final float dotRadius = DOT_RADIUS_DP * density;

        Log.d("AC_RETICLE", "onDraw " + w + "x" + h + " ring=" + ringRadius + " dot=" + dotRadius +
               " ringStyle=" + ringPaint.getStyle() + " hasBg=" + (getBackground() != null));

        // 1) Hollow ring (stroke only).
        canvas.drawCircle(cx, cy, ringRadius, ringPaint);
        // 2) Solid center dot (the aiming point).
        canvas.drawCircle(cx, cy, dotRadius, dotPaint);
    }
}
