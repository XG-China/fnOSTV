package com.fnostv.android4.ui;

import android.content.Context;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

public final class StatusOverlay {
    /** 兜底：任何提示最多显示这么久，避免某个请求卡住后一直盖住整个界面。 */
    private static final long AUTO_HIDE_MS = 20000L;

    private final TextView view;
    private final Handler handler = new Handler();
    private final Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            view.setVisibility(View.GONE);
        }
    };

    public StatusOverlay(Context context) {
        view = new TextView(context);
        view.setTextColor(0xFFFFFFFF);
        view.setTextSize(18);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(0xCC101820);
        view.setVisibility(View.GONE);
    }

    public TextView getView() {
        return view;
    }

    public boolean isVisible() {
        return view.getVisibility() == View.VISIBLE;
    }

    public void show(String message) {
        view.setText(message);
        view.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, AUTO_HIDE_MS);
    }

    public void hide() {
        handler.removeCallbacks(hideRunnable);
        view.setVisibility(View.GONE);
    }

    public static FrameLayout.LayoutParams layoutParams() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }
}
