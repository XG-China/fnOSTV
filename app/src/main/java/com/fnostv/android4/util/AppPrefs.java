package com.fnostv.android4.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * TV 端本地偏好（主题模式、播放偏好、卡片样式）。
 * app 只负责观看：影视服务器侧的设置一律在真实服务器网页端修改，这里只留观看需要的开关。
 */
public final class AppPrefs {
    /** 主题模式：dark / light / schedule（日升日落）。 */
    public static final String KEY_THEME_MODE = "theme_mode";
    /** 日升日落模式的日出时间，形如 "06:00"。 */
    public static final String KEY_THEME_SUNRISE = "theme_sunrise";
    /** 日升日落模式的日落时间，形如 "18:00"。 */
    public static final String KEY_THEME_SUNSET = "theme_sunset";
    /** 剧集/同目录多个视频播放完自动连播下一集。 */
    public static final String KEY_AUTO_NEXT = "auto_next";
    /** 硬件解码优先（关闭则全程软解）。 */
    public static final String KEY_HW_DECODER = "hw_decoder";
    /**
     * 原画直连（电视自带硬解）：4K/H.265 交给电视芯片解码。
     * 个别机型（MTK 4K）在频繁起停时会导致整机重启，可用它一键关掉改走转码。
     */
    public static final String KEY_DIRECT_HW_PLAY = "direct_hw_play";
    /** 卡片角标：评分。 */
    public static final String KEY_CARD_SCORE = "card_score";
    /** 卡片角标：“已观看”标记。 */
    public static final String KEY_CARD_WATCHED = "card_watched";
    /** 卡片角标：分辨率。 */
    public static final String KEY_CARD_RESOLUTION = "card_resolution";
    /** 电视直播的 IPTV 播放列表地址（m3u / txt），为空则显示服务器自带的直播频道列表。 */
    public static final String KEY_IPTV_URL = "iptv_url";
    /** 上次看的直播分类（下次进直播页时直接定位）。 */
    public static final String KEY_IPTV_GROUP = "iptv_group";
    /** 最后播放的条目（电视意外重启后用来判断是不是播这个片子导致的）。 */
    public static final String KEY_LAST_PLAY_NAME = "last_play_name";
    public static final String KEY_LAST_PLAY_URL = "last_play_url";
    public static final String KEY_LAST_PLAY_AT = "last_play_at";

    private AppPrefs() {
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean bool(Context context, String key, boolean fallback) {
        if (context == null) {
            return fallback;
        }
        return preferences(context).getBoolean(key, fallback);
    }

    public static void putBool(Context context, String key, boolean value) {
        if (context != null) {
            preferences(context).edit().putBoolean(key, value).apply();
        }
    }

    public static String string(Context context, String key, String fallback) {
        if (context == null) {
            return fallback;
        }
        String value = preferences(context).getString(key, fallback);
        return value == null || value.length() == 0 ? fallback : value;
    }

    public static void putString(Context context, String key, String value) {
        if (context != null) {
            preferences(context).edit().putString(key, value).apply();
        }
    }

    public static long longValue(Context context, String key, long fallback) {
        if (context == null) {
            return fallback;
        }
        return preferences(context).getLong(key, fallback);
    }

    public static void putLong(Context context, String key, long value) {
        if (context != null) {
            preferences(context).edit().putLong(key, value).apply();
        }
    }
}
