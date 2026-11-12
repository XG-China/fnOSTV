package com.fnostv.android4.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.widget.FrameLayout;

/**
 * 圆角容器：把子视图（封面图）按圆角裁剪。
 *
 * Android 4.4 的 ImageView 自己不会裁剪图片，直接放上去会盖住圆角背景，
 * 这里在 dispatchDraw 里做圆角裁剪，海报就能像现代 UI 一样是圆角。
 */
public final class RoundedFrameLayout extends FrameLayout {
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    private final float[] radiiPx;

    public RoundedFrameLayout(Context context, int radiusDp) {
        this(context, radiusDp, radiusDp);
    }

    /** 可分别指定上/下圆角（例如封面只圆上面两角）。 */
    public RoundedFrameLayout(Context context, int topRadiusDp, int bottomRadiusDp) {
        super(context);
        float top = FnosTheme.dp(context, topRadiusDp);
        float bottom = FnosTheme.dp(context, bottomRadiusDp);
        this.radiiPx = new float[]{top, top, top, top, bottom, bottom, bottom, bottom};
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            super.dispatchDraw(canvas);
            return;
        }
        rect.set(0, 0, getWidth(), getHeight());
        clipPath.reset();
        clipPath.addRoundRect(rect, radiiPx, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clipPath);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }
}
