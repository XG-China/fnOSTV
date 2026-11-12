package com.fnostv.android4.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class MediaDetailInfoParser {
    private MediaDetailInfoParser() {
    }

    /** 类型 id→名称表（GET /v/api/v1/tag/genres）。 */
    public static final class GenreTable {
        private final java.util.Map<Integer, String> names = new java.util.HashMap<Integer, String>();

        public void put(int id, String name) {
            if (name != null && name.length() > 0) {
                names.put(id, name);
            }
        }

        public String nameOf(int id) {
            String value = names.get(id);
            return value == null ? "" : value;
        }

        /** 网页样式："动画 · 喜剧 · 家庭 · 科幻奇幻"。 */
        public String joinNames(JSONArray ids) {
            if (ids == null || ids.length() == 0) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < ids.length(); i++) {
                String name = nameOf(ids.optInt(i, -1));
                if (name.length() == 0) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(" · ");
                }
                builder.append(name);
            }
            return builder.toString();
        }
    }

    public static MediaDetailInfo parse(JSONObject response) {
        JSONObject item = detailObject(response);
        if (item == null) {
            item = response == null ? new JSONObject() : response;
        }
        return new MediaDetailInfo(
                firstNonEmpty(item, "title", "name", "tv_title", "file_name"),
                firstNonEmpty(item, "original_title", "subtitle", "season_title", "album_title"),
                firstNonEmpty(item, "overview", "description", "plot", "summary", "intro"),
                year(item),
                rating(item),
                category(item),
                duration(item),
                firstNonEmpty(item, "source_label", "sourceLabel", "type", "media_type"),
                children(item));
    }

    private static JSONObject detailObject(JSONObject response) {
        if (response == null) {
            return null;
        }
        JSONObject data = response.optJSONObject("data");
        if (data == null) {
            return response;
        }
        JSONObject item = data.optJSONObject("item");
        if (item != null) {
            return item;
        }
        JSONObject detail = data.optJSONObject("detail");
        if (detail != null) {
            return detail;
        }
        return data;
    }

    private static List<FnosFileEntry> children(JSONObject item) {
        List<FnosFileEntry> entries = new ArrayList<FnosFileEntry>();
        String[] keys = {"children", "episodes", "seasons", "versions", "sources", "list"};
        for (int i = 0; i < keys.length; i++) {
            JSONArray array = item.optJSONArray(keys[i]);
            if (array != null) {
                appendChildren(entries, array);
            }
        }
        return entries;
    }

    private static void appendChildren(List<FnosFileEntry> entries, JSONArray array) {
        for (int i = 0; i < array.length(); i++) {
            JSONObject child = array.optJSONObject(i);
            if (child == null) {
                continue;
            }
            FnosFileEntry entry = childEntry(child);
            if (entry.path.length() > 0 && !containsPath(entries, entry.path)) {
                entries.add(entry);
            }
        }
    }

    private static FnosFileEntry childEntry(JSONObject item) {
        String type = firstNonEmpty(item, "type", "media_type");
        String name = firstNonEmpty(item, "title", "name", "tv_title", "file_name", "guid");
        String path = firstNonEmpty(item, "path", "file_path", "location", "url", "single_child_guid", "guid");
        boolean directory = isDirectoryType(type);
        return new FnosFileEntry(
                name,
                path,
                directory,
                item.optLong("size", 0L),
                type,
                mediaUrl(item),
                posterPath(item));
    }

    private static String year(JSONObject item) {
        String value = firstNonEmpty(item, "year", "release_year");
        if (value.length() > 0) {
            return value;
        }
        String date = firstNonEmpty(item, "release_date", "air_date", "date");
        return date.length() >= 4 ? date.substring(0, 4) : "";
    }

    private static String rating(JSONObject item) {
        String value = firstNonEmpty(item, "rating", "score", "douban_score", "tmdb_score", "vote_average");
        if (value.endsWith(".0")) {
            return value.substring(0, value.length() - 2);
        }
        return value;
    }

    private static String category(JSONObject item) {
        String value = firstNonEmpty(item, "category", "type", "media_type");
        if (value.length() > 0) {
            return value;
        }
        JSONObject tags = item.optJSONObject("tags");
        return tags == null ? "" : tags.optString("type");
    }

    private static String duration(JSONObject item) {
        String value = firstNonEmpty(item, "duration_label", "durationLabel", "duration_text");
        if (value.length() > 0 && !isNumeric(value)) {
            return value;
        }
        // 官方 item 接口的 duration 为文件时长（秒），网页按它显示"1 小时 31 分钟"。
        long seconds = item.optLong("duration", -1L);
        if (seconds < 0 && isNumeric(value)) {
            seconds = Long.parseLong(value);
        }
        if (seconds > 24L * 60L * 60L) {
            seconds = seconds / 1000L;
        }
        if (seconds > 0) {
            return formatMinutes(seconds / 60L);
        }
        long runtimeMinutes = item.optLong("runtime", -1L);
        if (runtimeMinutes > 0) {
            return formatMinutes(runtimeMinutes);
        }
        return "";
    }

    private static String formatMinutes(long minutes) {
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

    private static String firstNonEmpty(JSONObject item, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            String value = item.optString(keys[i]);
            if (value != null && value.trim().length() > 0 && !"null".equals(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.length() == 0) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String posterPath(JSONObject item) {
        String value = item.optString("poster");
        if (value.length() > 0) {
            return value;
        }
        JSONArray posters = item.optJSONArray("poster_list");
        return posters != null && posters.length() > 0 ? posters.optString(0) : "";
    }

    private static String mediaUrl(JSONObject item) {
        String[] keys = {"playUrl", "downloadUrl", "fileUrl", "mediaUrl", "realUrl", "link", "src", "url"};
        for (int i = 0; i < keys.length; i++) {
            String value = item.optString(keys[i]);
            if (value.startsWith("http://") || value.startsWith("https://")) {
                return value;
            }
        }
        return "";
    }

    private static boolean isDirectoryType(String type) {
        String value = type == null ? "" : type.toLowerCase();
        return "directory".equals(value)
                || "tv".equals(value)
                || "season".equals(value)
                || "mediadb".equals(value);
    }

    private static boolean containsPath(List<FnosFileEntry> entries, String path) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).path.equals(path)) {
                return true;
            }
        }
        return false;
    }
}
