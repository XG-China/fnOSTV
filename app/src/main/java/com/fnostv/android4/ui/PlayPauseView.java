package com.fnostv.android4.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** 播放器左下角的播放/暂停图标：播放时画两竖条，暂停时画三角形。 */
public final class PlayPauseView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private boolean playing;

    public PlayPauseView(Context context) {
        super(context);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setPlaying(boolean value) {
        if (playing != value) {
            playing = value;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float size = Math.min(w, h) * 0.62f;
        float left = (w - size) / 2f;
        float top = (h - size) / 2f;
        if (playing) {
            float barWidth = size * 0.3f;
            float gap = size * 0.16f;
            float radius = barWidth * 0.2f;
            // 4.4 没有 drawRoundRect(l,t,r,b,..)，统一用 RectF 版本
            rect.set(left, top, left + barWidth, top + size);
            canvas.drawRoundRect(rect, radius, radius, paint);
            rect.set(left + barWidth + gap, top, left + size, top + size);
            canvas.drawRoundRect(rect, radius, radius, paint);
        } else {
            path.reset();
            path.moveTo(left + size * 0.06f, top);
            path.lineTo(left + size, top + size / 2f);
            path.lineTo(left + size * 0.06f, top + size);
            path.close();
            canvas.drawPath(path, paint);
        }
    }
}
