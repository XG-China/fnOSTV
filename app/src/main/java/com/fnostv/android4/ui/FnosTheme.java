package com.fnostv.android4.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import com.fnostv.android4.util.AppPrefs;

public final class FnosTheme {
    /** 主题模式：深色。 */
    public static final String MODE_DARK = "dark";
    /** 主题模式：浅色。 */
    public static final String MODE_LIGHT = "light";
    /** 主题模式：日升日落（白天浅色、夜间深色，替代网页端的“跟随系统”）。 */
    public static final String MODE_SCHEDULE = "schedule";
    public static final String DEFAULT_SUNRISE = "06:00";
    public static final String DEFAULT_SUNSET = "18:00";

    /** 深色调色板（默认，与网页深色主题一致）。 */
    private static final int[] DARK_PALETTE = {
            0xFF0B0E13, // COLOR_APP_BG
            0xFF0E1218, // COLOR_SIDEBAR
            0xF2141A22, // COLOR_PANEL
            0xFF171C25, // COLOR_CARD
            0xFF1F2B3D, // COLOR_CARD_FOCUSED
            0xFF2F7CFF, // COLOR_PRIMARY
            0xFFE9EEF5, // COLOR_TEXT
            0xFFDCE3EC, // COLOR_TEXT_80
            0xFFB4BECC, // COLOR_TEXT_60
            0xFF8B96A5, // COLOR_TEXT_MUTED
            0xFF6A7482, // COLOR_TEXT_DIM
            0xFF232A35, // COLOR_STROKE
            0x1AFFFFFF, // COLOR_HAIRLINE
    };
    /** 浅色调色板（柔和的米白/灰白，避免大面积纯白刺眼）。 */
    private static final int[] LIGHT_PALETTE = {
            0xFFEDEFF3, // COLOR_APP_BG
            0xFFF7F8FA, // COLOR_SIDEBAR
            0xF2FCFCFD, // COLOR_PANEL
            0xFFFAFBFC, // COLOR_CARD
            0xFFE8F0FB, // COLOR_CARD_FOCUSED
            0xFF2F7CFF, // COLOR_PRIMARY
            0xFF1B1F24, // COLOR_TEXT
            0xFF2E343B, // COLOR_TEXT_80
            0xFF5A6472, // COLOR_TEXT_60
            0xFF6E7886, // COLOR_TEXT_MUTED
            0xFF8D97A4, // COLOR_TEXT_DIM
            0xFFE0E4EA, // COLOR_STROKE
            0x14000000, // COLOR_HAIRLINE
    };

    public static int COLOR_APP_BG = DARK_PALETTE[0];
    public static int COLOR_SIDEBAR = DARK_PALETTE[1];
    public static int COLOR_PANEL = DARK_PALETTE[2];
    public static int COLOR_CARD = DARK_PALETTE[3];
    public static int COLOR_CARD_FOCUSED = DARK_PALETTE[4];
    public static int COLOR_PRIMARY = DARK_PALETTE[5];
    public static int COLOR_TEXT = DARK_PALETTE[6];
    public static int COLOR_TEXT_80 = DARK_PALETTE[7];
    public static int COLOR_TEXT_60 = DARK_PALETTE[8];
    public static int COLOR_TEXT_MUTED = DARK_PALETTE[9];
    public static int COLOR_TEXT_DIM = DARK_PALETTE[10];
    public static int COLOR_STROKE = DARK_PALETTE[11];
    public static int COLOR_HAIRLINE = DARK_PALETTE[12];
    /** 主色按钮的聚焦色（比主色更亮，遥控器焦点更明显）。 */
    public static int COLOR_PRIMARY_FOCUSED = 0xFF4C92FF;
    /** 封面/缩略图未加载时的占位底色（浅色主题下不要用纯白）。 */
    public static int COLOR_PLACEHOLDER = 0xFF1A1F27;
    /** 以下三个与主题无关，深浅色下观感都成立。 */
    public static final int COLOR_SCORE_BG = 0x99000000;
    public static final int COLOR_SCORE = 0xFFF2C214;
    public static final int COLOR_ERROR = 0xFF64292F;

    private FnosTheme() {
    }

    /** 应用浅色/深色调色板（切换后需要重建界面才对已创建的视图生效）。 */
    public static void applyPalette(boolean light) {
        int[] palette = light ? LIGHT_PALETTE : DARK_PALETTE;
        COLOR_APP_BG = palette[0];
        COLOR_SIDEBAR = palette[1];
        COLOR_PANEL = palette[2];
        COLOR_CARD = palette[3];
        COLOR_CARD_FOCUSED = palette[4];
        COLOR_PRIMARY = palette[5];
        COLOR_TEXT = palette[6];
        COLOR_TEXT_80 = palette[7];
        COLOR_TEXT_60 = palette[8];
        COLOR_TEXT_MUTED = palette[9];
        COLOR_TEXT_DIM = palette[10];
        COLOR_STROKE = palette[11];
        COLOR_HAIRLINE = palette[12];
        COLOR_PLACEHOLDER = light ? 0xFFE6EAF0 : 0xFF1A1F27;
    }

    /**
     * 按本地偏好应用主题（深色 / 浅色 / 日升日落）。
     * 返回当前生效签名，调用方用于比对“设置里改了主题”后重建界面。
     */
    public static String applyFromPrefs(Context context) {
        String mode = AppPrefs.string(context, AppPrefs.KEY_THEME_MODE, MODE_DARK);
        boolean light = MODE_LIGHT.equals(mode)
                || (MODE_SCHEDULE.equals(mode) && isDaytime(context));
        applyPalette(light);
        return mode + "|" + light;
    }

    /** 日升日落模式：当前是否白天（日出 ≤ 现在 &lt; 日落）。 */
    public static boolean isDaytime(Context context) {
        int minute = currentMinuteOfDay();
        int sunrise = minuteOfDay(AppPrefs.string(context, AppPrefs.KEY_THEME_SUNRISE, DEFAULT_SUNRISE));
        int sunset = minuteOfDay(AppPrefs.string(context, AppPrefs.KEY_THEME_SUNSET, DEFAULT_SUNSET));
        if (sunrise == sunset) {
            return true;
        }
        return sunrise <= sunset
                ? minute >= sunrise && minute < sunset
                : (minute >= sunrise || minute < sunset);
    }

    /** "HH:mm" → 当天第几分钟；无法解析时回落到 0。 */
    public static int minuteOfDay(String value) {
        if (value == null) {
            return 0;
        }
        int separator = value.indexOf(':');
        if (separator <= 0) {
            return 0;
        }
        try {
            int hour = Integer.parseInt(value.substring(0, separator).trim());
            int minute = Integer.parseInt(value.substring(separator + 1).trim());
            return Math.max(0, Math.min(23, hour)) * 60 + Math.max(0, Math.min(59, minute));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static int currentMinuteOfDay() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        return calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE);
    }

    public static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static GradientDrawable rounded(int color, int radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    /** 上下不同圆角的圆角矩形（封面：上面圆、下面直角）。 */
    public static GradientDrawable rounded(int color, int topRadiusDp, int bottomRadiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float top = dp(context, topRadiusDp);
        float bottom = dp(context, bottomRadiusDp);
        drawable.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        return drawable;
    }

    /** 是否浅色主题（用于玻璃效果的明暗选择）。 */
    public static boolean isLightTheme() {
        int color = COLOR_APP_BG;
        float luminance = (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f;
        return luminance > 0.5f;
    }

    /** 玻璃面板：竖向渐变 + 淡描边（未聚焦）。 */
    public static Drawable glass(Context context, int radiusDp) {
        if (isLightTheme()) {
            return new GlassDrawable(context, 0x59FFFFFF, 0x1AFFFFFF, 0x40000000, 0x14000000, radiusDp, 1);
        }
        return new GlassDrawable(context, 0x1FFFFFFF, 0x0AFFFFFF, 0x33FFFFFF, 0x14FFFFFF, radiusDp, 1);
    }

    /** 玻璃焦点态：蓝调渐变边框（上亮下暗）+ 外侧暗描边，浅色封面上也清晰。 */
    public static Drawable glassFocused(Context context, int radiusDp) {
        return glassFocused(context, radiusDp, 2);
    }

    /** 大尺寸卡片用更粗的焦点框，远距离也一眼看到。 */
    public static Drawable glassFocused(Context context, int radiusDp, int strokeDp) {
        return new GlassDrawable(context, 0x3D4C92FF, 0x142F7CFF,
                0xFFD8ECFF, 0xE64C92FF, radiusDp, strokeDp, 0, 0);
    }

    /** 只画渐变边框（用于列表选中框，不遮挡封面）。 */
    public static Drawable glassOutline(Context context, int radiusDp, int strokeDp) {
        return new GlassDrawable(context, Color.TRANSPARENT, Color.TRANSPARENT,
                0xFFD8ECFF, 0xE64C92FF, radiusDp, strokeDp, 0, 0);
    }

    /** 上圆角、下直角的选中框（与封面形状一致）。 */
    public static Drawable glassOutlineTopRounded(Context context, int topRadiusDp, int strokeDp) {
        return new GlassDrawable(context, Color.TRANSPARENT, Color.TRANSPARENT,
                0xFFD8ECFF, 0xE64C92FF, topRadiusDp, strokeDp, 0, 0)
                .cornerRadiiPx(dp(context, topRadiusDp), 0f);
    }

    /** 焦点态：圆角 + 2dp 主色描边。 */
    public static GradientDrawable ring(int color, int strokeColor, int radiusDp, Context context) {
        GradientDrawable drawable = rounded(color, radiusDp, context);
        drawable.setStroke(dp(context, 2), strokeColor);
        return drawable;
    }

}
