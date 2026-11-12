package com.fnostv.android4.net;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 电视剧 / 季页面头部的数据（网页端 hero 头部）。
 *
 * <p>官方 v1 没有 {@code GET /tv/:guid}（返回 Not Implemented），
 * 所以剧名、简介、海报、评分都从季条目 {@code GET /item/<seasonGuid>} 取：
 * 该接口的 {@code tv_title} 是剧名，{@code overview} 是整部剧的简介，
 * {@code poster} 是剧集海报，{@code season_number} 是当前季号。
 */
public final class MediaSeriesHeader {
    /** 主标题（剧名；季页仍是剧名）。 */
    public final String title;
    /** 副标题（季页「第 N 季」，剧集页为空）。 */
    public final String seasonTitle;
    /** 简介。 */
    public final String overview;
    /** 海报路径（/xx/yy/xxx.jpg）。 */
    public final String posterPath;
    /** 年份（release_date 前 4 位）。 */
    public final String year;
    /** 评分（保留一位小数，0 分返回空串）。 */
    public final String score;
    /** 本地季数。 */
    public final int seasonCount;
    /** 本地集数（季页为该季集数）。 */
    public final int episodeCount;
    /** 原始类型（TV / Season / Episode）。 */
    public final String type;
    /** 季条目的 parent_guid，即所属电视剧 guid（用于加载季列表）。 */
    public final String tvGuid;
    /** 横向背景图（网页剧页顶部大图，来自 item.backdrops）。 */
    public final String backdropPath;
    /** 剧名 Logo（item.logos，透明 webp）。 */
    public final String logoPath;

    public MediaSeriesHeader(String title, String seasonTitle, String overview, String posterPath,
                             String year, String score, int seasonCount, int episodeCount, String type) {
        this(title, seasonTitle, overview, posterPath, year, score, seasonCount, episodeCount, type, "");
    }

    public MediaSeriesHeader(String title, String seasonTitle, String overview, String posterPath,
                             String year, String score, int seasonCount, int episodeCount, String type,
                             String tvGuid) {
        this(title, seasonTitle, overview, posterPath, year, score, seasonCount, episodeCount, type,
                tvGuid, "", "");
    }

    public MediaSeriesHeader(String title, String seasonTitle, String overview, String posterPath,
                             String year, String score, int seasonCount, int episodeCount, String type,
                             String tvGuid, String backdropPath, String logoPath) {
        this.title = clean(title);
        this.seasonTitle = clean(seasonTitle);
        this.overview = clean(overview);
        this.posterPath = clean(posterPath);
        this.year = clean(year);
        this.score = clean(score);
        this.seasonCount = Math.max(0, seasonCount);
        this.episodeCount = Math.max(0, episodeCount);
        this.type = clean(type);
        this.tvGuid = clean(tvGuid);
        this.backdropPath = clean(backdropPath);
        this.logoPath = clean(logoPath);
    }

    public static MediaSeriesHeader empty() {
        return new MediaSeriesHeader("", "", "", "", "", "", 0, 0, "");
    }

    public boolean isEmpty() {
        return title.length() == 0 && overview.length() == 0 && posterPath.length() == 0;
    }

    /** 网页端季号文案：第 N 季 / 未知季。 */
    public static String seasonLabel(int seasonNumber) {
        return seasonNumber < 0 ? "未知季" : "第 " + seasonNumber + " 季";
    }

    /** 从 {@code GET /item/<guid>} 的 data 对象构造。 */
    public static MediaSeriesHeader fromItem(JSONObject item, String fallbackTitle) {
        if (item == null) {
            return empty();
        }
        String tvTitle = item.optString("tv_title");
        String rawTitle = item.optString("title");
        String title = firstNonEmpty(tvTitle, rawTitle, fallbackTitle);
        int seasonNumber = item.optInt("season_number", 0);
        int seasons = firstPositive(item.optInt("local_number_of_seasons", 0),
                item.optInt("number_of_seasons", 0));
        int episodes = firstPositive(item.optInt("local_number_of_episodes", 0),
                item.optInt("number_of_episodes", 0));
        String poster = imagePath(item, "poster", "posters");
        return new MediaSeriesHeader(
                title,
                "Season".equalsIgnoreCase(item.optString("type")) ? seasonLabel(seasonNumber) : "",
                item.optString("overview"),
                poster,
                yearOf(item),
                scoreOf(item),
                seasons,
                episodes,
                item.optString("type"),
                item.optString("parent_guid"),
                imagePath(item, "backdrop", "backdrops"),
                imagePath(item, "logo", "logos"));
    }

    /**
     * 图片字段兼容不同的刮削方式：既可能是 {@code "backdrops":"/1/2/a.jpg"}，
     * 也可能是 {@code "backdrops":["/1/2/a.jpg"]} 或 [{path:...}] 数组。
     */
    private static String imagePath(JSONObject item, String singleKey, String arrayKey) {
        String value = clean(item.optString(singleKey));
        if (value.length() > 0 && !value.startsWith("[")) {
            return value;
        }
        value = clean(item.optString(arrayKey));
        if (value.length() > 0 && !value.startsWith("[")) {
            return value;
        }
        JSONArray array = item.optJSONArray(arrayKey);
        if (array == null) {
            array = item.optJSONArray(singleKey);
        }
        if (array != null && array.length() > 0) {
            Object first = array.opt(0);
            if (first instanceof JSONObject) {
                return clean(((JSONObject) first).optString("path"));
            }
            return clean(String.valueOf(first));
        }
        return "";
    }

    private static int firstPositive(int first, int second) {
        return first > 0 ? first : Math.max(0, second);
    }

    private static String yearOf(JSONObject item) {
        String value = firstNonEmpty(item.optString("release_date"), item.optString("air_date"));
        if (value.length() >= 4) {
            return value.substring(0, 4);
        }
        return "";
    }

    private static String scoreOf(JSONObject item) {
        String value = item.optString("vote_average");
        try {
            double number = Double.parseDouble(value);
            if (number > 0) {
                return String.format(java.util.Locale.US, "%.1f", number);
            }
        } catch (NumberFormatException ignored) {
        }
        return "";
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            if (value != null && value.trim().length() > 0 && !"null".equals(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
