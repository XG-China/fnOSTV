package com.fnostv.android4.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * 自绘开关：Android 4.4 自带 Switch 在部分电视主题下会渲染成灰块，
 * 这里用 Canvas 画一个现代胶囊开关，深浅色主题都稳定。
 */
public final class ModernSwitch extends View {
    public interface OnCheckedChangeListener {
        void onCheckedChanged(ModernSwitch view, boolean checked);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean checked;
    private OnCheckedChangeListener listener;

    public ModernSwitch(Context context) {
        super(context);
        setFocusable(true);
        setClickable(true);
        knobPaint.setColor(0xFFFFFFFF);
        strokePaint.setStyle(Paint.Style.STROKE);
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean value) {
        if (checked != value) {
            checked = value;
            invalidate();
        }
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener value) {
        listener = value;
    }

    @Override
    public boolean performClick() {
        checked = !checked;
        invalidate();
        if (listener != null) {
            listener.onCheckedChanged(this, checked);
        }
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(FnosTheme.dp(getContext(), 52), FnosTheme.dp(getContext(), 30));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float padding = FnosTheme.dp(getContext(), 2);
        float radius = getHeight() / 2f;
        rect.set(padding, padding, getWidth() - padding, getHeight() - padding);

        trackPaint.setColor(checked ? FnosTheme.COLOR_PRIMARY : FnosTheme.COLOR_STROKE);
        canvas.drawRoundRect(rect, radius, radius, trackPaint);

        if (isFocused()) {
            strokePaint.setStrokeWidth(FnosTheme.dp(getContext(), 2));
            strokePaint.setColor(FnosTheme.COLOR_PRIMARY_FOCUSED);
            canvas.drawRoundRect(rect, radius, radius, strokePaint);
        }

        float knobRadius = radius - FnosTheme.dp(getContext(), 5);
        float centerY = getHeight() / 2f;
        float centerX = checked
                ? getWidth() - padding - knobRadius - FnosTheme.dp(getContext(), 2)
                : padding + knobRadius + FnosTheme.dp(getContext(), 2);
        canvas.drawCircle(centerX, centerY, knobRadius, knobPaint);
    }
}
