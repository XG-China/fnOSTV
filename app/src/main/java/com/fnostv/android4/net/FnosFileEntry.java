package com.fnostv.android4.net;

import org.json.JSONObject;

public final class FnosFileEntry {
    public final String name;
    public final String path;
    public final boolean directory;
    public final long size;
    public final String type;
    public final String mediaUrl;
    public final String posterPath;
    public final String year;
    public final String score;
    /** 网页同款：服务器判定的“已观看”标记（item.watched）。 */
    public final boolean watched;
    /** 分辨率标签（item.media_stream.resolutions，如 "4k"/"1080p"），空则由扩展名兜底。 */
    public final String resolution;
    /** 片长（item.duration，秒）。 */
    public final long durationSeconds;
    /** 季号（episode 列表用）。 */
    public final int seasonNumber;
    /** 集号（episode 列表用）。 */
    public final int episodeNumber;
    /** 所属媒体库名称（item/list 的 ancestor_name，网页文件夹页标题下的副标题）。 */
    public final String libraryName;
    /** 父文件夹名称（item/list 的 parent_title，网页文件夹页的主标题）。 */
    public final String parentTitle;
    /** 剧情简介（item 的 overview，剧集卡片上显示两行）。 */
    public final String overview;
    /** 季内集数（item 的 local_number_of_episodes，季卡片副标题「N 集」）。 */
    public final int episodeCount;
    /** 单子条目 guid（item 的 single_child_guid；电视剧只有一季时网页直接进季页）。 */
    public final String singleChildGuid;
    /** 剧集所属季 guid（item 的 parent_guid），用于从继续观看直接进入剧集页。 */
    public final String parentGuid;
    /** 剧集所属电视剧 guid（item 的 ancestor_guid）。 */
    public final String ancestorGuid;
    /** 剧名（item 的 tv_title）。 */
    public final String tvTitle;

    public FnosFileEntry(String name, String path, boolean directory, long size, String type, String mediaUrl) {
        this(name, path, directory, size, type, mediaUrl, "");
    }

    public FnosFileEntry(String name, String path, boolean directory, long size, String type, String mediaUrl, String posterPath) {
        this(name, path, directory, size, type, mediaUrl, posterPath, "", "");
    }

    public FnosFileEntry(String name, String path, boolean directory, long size, String type, String mediaUrl, String posterPath, String year, String score) {
        this(name, path, directory, size, type, mediaUrl, posterPath, year, score, false, "", 0L);
    }

    public FnosFileEntry(String name, String path, boolean directory, long size, String type, String mediaUrl,
                         String posterPath, String year, String score, boolean watched, String resolution, long durationSeconds) {
        this(name, path, directory, size, type, mediaUrl, posterPath, year, score, watched, resolution, durationSeconds, 0, 0);
    }

    public FnosFileEntry(String name, String path, boolean directory, long size, String type, String mediaUrl,
                         String posterPath, String year, String score, boolean watched, String resolution, long durationSeconds,
                         int seasonNumber, int episodeNumber) {
        this(name, path, directory, size, type, mediaUrl, posterPath, year, score, watched, resolution, durationSeconds,
                seasonNumber, episodeNumber, "");
    }

    public FnosFileEntry(String name, String path, boolean directory, long size, String type, String mediaUrl,
                         String posterPath, String year, String score, boolean watched, String resolution, long durationSeconds,
                         int seasonNumber, int episodeNumber, String libraryName) {
        this(name, path, directory, size, type, mediaUrl, posterPath, year, score, watched, resolution, durationSeconds,
                seasonNumber, episodeNumber, libraryName, "");
    }

    public FnosFileEntry(String name, String path, boolean directory, long size, String type, String mediaUrl,
                         String posterPath, String year, String score, boolean watched, String resolution, long durationSeconds,
                         int seasonNumber, int episodeNumber, String libraryName, String parentTitle) {
        this(name, path, directory, size, type, mediaUrl, posterPath, year, score, watched, resolution, durationSeconds,
                seasonNumber, episodeNumber, libraryName, parentTitle, "", 0, "");
    }

    public FnosFileEntry(String name, String path, boolean directory, long size, String type, String mediaUrl,
                         String posterPath, String year, String score, boolean watched, String resolution, long durationSeconds,
                         int seasonNumber, int episodeNumber, String libraryName, String parentTitle,
                         String overview, int episodeCount, String singleChildGuid) {
        this(name, path, directory, size, type, mediaUrl, posterPath, year, score, watched, resolution,
                durationSeconds, seasonNumber, episodeNumber, libraryName, parentTitle, overview,
                episodeCount, singleChildGuid, "", "", "");
    }

    public FnosFileEntry(String name, String path, boolean directory, long size, String type, String mediaUrl,
                         String posterPath, String year, String score, boolean watched, String resolution, long durationSeconds,
                         int seasonNumber, int episodeNumber, String libraryName, String parentTitle,
                         String overview, int episodeCount, String singleChildGuid,
                         String parentGuid, String ancestorGuid, String tvTitle) {
        this.name = name == null ? "" : name;
        this.path = path == null ? "" : path;
        this.directory = directory;
        this.size = size;
        this.type = type == null ? "" : type;
        this.mediaUrl = mediaUrl == null ? "" : mediaUrl;
        this.posterPath = posterPath == null ? "" : posterPath;
        this.year = year == null ? "" : year;
        this.score = score == null ? "" : score;
        this.watched = watched;
        this.resolution = resolution == null ? "" : resolution;
        this.durationSeconds = durationSeconds < 0L ? 0L : durationSeconds;
        // season_number / episode_number = -1 表示「未知季 / 未知集」，必须保留原值，
        // 否则会被当成第 0 季、第 0 集（网页端显示「未知季 · 未知集」）。
        this.seasonNumber = seasonNumber;
        this.episodeNumber = episodeNumber;
        this.libraryName = libraryName == null ? "" : libraryName;
        this.parentTitle = parentTitle == null ? "" : parentTitle;
        this.overview = overview == null ? "" : overview;
        this.episodeCount = episodeCount < 0 ? 0 : episodeCount;
        this.singleChildGuid = singleChildGuid == null ? "" : singleChildGuid;
        this.parentGuid = parentGuid == null ? "" : parentGuid;
        this.ancestorGuid = ancestorGuid == null ? "" : ancestorGuid;
        this.tvTitle = tvTitle == null ? "" : tvTitle;
    }

    static FnosFileEntry fromJson(JSONObject object, String parentPath, String uid) {
        String name = object.optString("name");
        String path = object.optString("path");
        if (path.length() == 0) {
            path = rootPath(object, parentPath, uid, name);
        }
        boolean directory = object.optInt("dir", 0) == 1 || object.optBoolean("dir");
        return new FnosFileEntry(
                name,
                path,
                directory,
                object.optLong("size", 0L),
                object.optString("type"),
                mediaUrl(object, path));
    }

    public boolean isVideo() {
        String value = lower(type);
        if (value.indexOf("video") >= 0) {
            return true;
        }
        value = lower(name);
        return value.endsWith(".mp4")
                || value.endsWith(".m4v")
                || value.endsWith(".mov")
                || value.endsWith(".3gp")
                || value.endsWith(".3gpp")
                || value.endsWith(".webm")
                || value.endsWith(".mkv")
                || value.endsWith(".avi")
                || value.endsWith(".wmv")
                || value.endsWith(".asf")
                || value.endsWith(".ts")
                || value.endsWith(".m2ts")
                || value.endsWith(".flv")
                || value.endsWith(".mpeg")
                || value.endsWith(".mpg")
                || value.endsWith(".vob")
                || value.endsWith(".m3u8")
                || value.endsWith(".rm")
                || value.endsWith(".rmvb");
    }

    public boolean canTryNativePlayback() {
        return isVideo() || isLive();
    }

    /** 直播频道（IPTV / 服务器 LiveChannel）：地址本身就是播放流，没有时长和选集。 */
    public boolean isLive() {
        String value = lower(type);
        if (value.indexOf("livechannel") >= 0 || value.indexOf("live") >= 0) {
            return true;
        }
        String link = lower(mediaUrl);
        return link.indexOf(".m3u8") >= 0
                || link.startsWith("rtsp://")
                || link.startsWith("rtmp://")
                || link.startsWith("udp://")
                || link.startsWith("rtp://");
    }

    public boolean prefersHardwarePlayback() {
        String value = extension();
        if (value.equals("rm") || value.equals("rmvb") || value.equals("avi") || value.equals("wmv")
                || value.equals("asf") || value.equals("flv") || value.equals("vob") || value.equals("mpeg")
                || value.equals("mpg")) {
            return false;
        }
        return true;
    }

    public String playbackUrl() {
        if (mediaUrl.length() > 0) {
            return mediaUrl;
        }
        return isHttpUrl(path) ? path : "";
    }

    public String mimeType() {
        if (type.length() > 0 && type.indexOf('/') > 0) {
            return type;
        }
        String value = lower(name);
        if (value.endsWith(".mp4") || value.endsWith(".m4v")) {
            return "video/mp4";
        }
        if (value.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (value.endsWith(".3gp")) {
            return "video/3gpp";
        }
        if (value.endsWith(".webm")) {
            return "video/webm";
        }
        if (value.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (value.endsWith(".ts") || value.endsWith(".m2ts")) {
            return "video/mp2t";
        }
        if (value.endsWith(".mpeg") || value.endsWith(".mpg") || value.endsWith(".vob")) {
            return "video/mpeg";
        }
        if (value.endsWith(".avi")) {
            return "video/x-msvideo";
        }
        if (value.endsWith(".wmv") || value.endsWith(".asf")) {
            return "video/x-ms-wmv";
        }
        if (value.endsWith(".flv")) {
            return "video/x-flv";
        }
        if (value.endsWith(".mkv")) {
            return "video/x-matroska";
        }
        if (value.endsWith(".rm") || value.endsWith(".rmvb")) {
            return "application/vnd.rn-realmedia";
        }
        return "video/*";
    }

    public String formatLabel() {
        String value = extension();
        return value.length() == 0 ? "video" : value.toUpperCase();
    }

    private static String rootPath(JSONObject object, String parentPath, String uid, String name) {
        if (parentPath != null && parentPath.length() > 0) {
            return join(parentPath, name);
        }
        String volume = object.optString("v");
        if (volume.length() == 0) {
            volume = object.optString("vid");
        }
        if (volume.length() > 0 && uid != null && uid.length() > 0) {
            return "vol" + volume + "/" + uid + "/" + name;
        }
        return name;
    }

    private static String join(String parentPath, String name) {
        if (parentPath == null || parentPath.length() == 0) {
            return name;
        }
        if (name == null || name.length() == 0) {
            return parentPath;
        }
        return parentPath.endsWith("/") ? parentPath + name : parentPath + "/" + name;
    }

    private static String mediaUrl(JSONObject object, String fallbackPath) {
        String[] keys = {"url", "playUrl", "downloadUrl", "fileUrl", "mediaUrl", "realUrl", "link", "src"};
        for (int i = 0; i < keys.length; i++) {
            String value = object.optString(keys[i]);
            if (isHttpUrl(value)) {
                return value;
            }
        }
        return isHttpUrl(fallbackPath) ? fallbackPath : "";
    }

    private static boolean isHttpUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    public int seasonNumber() {
        return seasonNumber;
    }

    public int episodeNumber() {
        return episodeNumber;
    }

    /** 网页端季号文案：第 N 季 / 未知季。 */
    public String seasonLabel() {
        return seasonNumber < 0 ? "未知季" : "第 " + seasonNumber + " 季";
    }

    /** 网页端集号文案：第 N 集 / 未知集。 */
    public String episodeLabel() {
        return episodeNumber < 0 ? "未知集" : "第 " + episodeNumber + " 集";
    }

    /** 剧集卡片标题：第 N 集. 剧集名（网页端 formattedTitle 同款）。 */
    public String episodeDisplayTitle() {
        if (name.length() == 0) {
            return episodeLabel();
        }
        return episodeLabel() + ". " + name;
    }

    /** 时长文案：41 分钟 / 1 小时 8 分钟；无时长返回空串。 */
    public String durationLabel() {
        if (durationSeconds <= 0L) {
            return "";
        }
        long minutes = Math.max(1L, durationSeconds / 60L);
        long hours = minutes / 60L;
        long remain = minutes % 60L;
        if (hours > 0 && remain > 0) {
            return hours + " 小时 " + remain + " 分钟";
        }
        if (hours > 0) {
            return hours + " 小时";
        }
        return remain + " 分钟";
    }

    private String extension() {
        String value = lower(name);
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        int index = value.lastIndexOf('.');
        if (index < 0 || index == value.length() - 1) {
            return "";
        }
        return value.substring(index + 1);
    }
}
