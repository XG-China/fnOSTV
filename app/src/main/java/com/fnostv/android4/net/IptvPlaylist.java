package com.fnostv.android4.net;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * IPTV 直播源解析：兼容两种常见格式。
 *
 * <pre>
 * #EXTM3U
 * #EXTINF:-1 tvg-logo="http://..." group-title="央视",CCTV1
 * http://x.x.x.x/xxx/index.m3u8
 * </pre>
 *
 * <pre>
 * 央视,#genre#
 * CCTV1,http://x.x.x.x/xxx/index.m3u8
 * </pre>
 */
public final class IptvPlaylist {
    /** 单个频道。 */
    public static final class Channel {
        public final String name;
        public final String url;
        public final String group;
        public final String logo;
        /** 服务器 LiveChannel 条目的 guid；IPTV 播单里的频道为空。 */
        public final String guid;

        Channel(String name, String url, String group, String logo) {
            this(name, url, group, logo, "");
        }

        Channel(String name, String url, String group, String logo, String guid) {
            this.name = name;
            this.url = url == null ? "" : url;
            this.group = normalizeGroup(group, name);
            this.logo = logo == null ? "" : logo;
            this.guid = guid == null ? "" : guid;
        }

        /** 服务器媒体库里的直播频道：播放地址要先走 /play/info 解析。 */
        public static Channel forServerItem(String name, String guid, String group) {
            return new Channel(name, "", group, "", guid);
        }
    }

    private IptvPlaylist() {
    }

    /** 按出现顺序分组的频道列表。 */
    public static Map<String, List<Channel>> group(List<Channel> channels) {
        Map<String, List<Channel>> groups = new LinkedHashMap<String, List<Channel>>();
        for (int i = 0; i < channels.size(); i++) {
            Channel channel = channels.get(i);
            List<Channel> bucket = groups.get(channel.group);
            if (bucket == null) {
                bucket = new ArrayList<Channel>();
                groups.put(channel.group, bucket);
            }
            bucket.add(channel);
        }
        return groups;
    }

    public static List<Channel> parse(String text) {        List<Channel> result = new ArrayList<Channel>();
        if (text == null || text.length() == 0) {
            return result;
        }
        String pendingName = null;
        String pendingLogo = "";
        String pendingGroup = "";
        String[] lines = text.split("\r?\n");
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.length() == 0) {
                continue;
            }
            if (line.startsWith("#EXTINF")) {
                pendingName = extinfName(line);
                pendingLogo = extinfAttr(line, "tvg-logo");
                pendingGroup = extinfAttr(line, "group-title");
                continue;
            }
            if (line.startsWith("#")) {
                // #EXTM3U、#genre# 等分节标记
                if (line.endsWith("#genre#")) {
                    String section = line.substring(0, line.length() - "#genre#".length()).trim();
                    if (section.length() > 0 && section.indexOf(',') != 0) {
                        pendingGroup = section.replace(",", "").trim();
                    }
                }
                continue;
            }
            String url = line;
            String name = pendingName;
            int comma = line.indexOf(',');
            if (comma > 0 && line.indexOf("://") > comma) {
                // txt 格式：频道名,地址
                name = line.substring(0, comma).trim();
                url = line.substring(comma + 1).trim();
            }
            pendingName = null;
            if (name == null || name.length() == 0) {
                name = url;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")
                    && !url.startsWith("rtsp://") && !url.startsWith("rtmp://")
                    && !url.startsWith("udp://") && !url.startsWith("rtp://")) {
                continue;
            }
            result.add(new Channel(name, url, pendingGroup, pendingLogo));
            pendingLogo = "";
        }
        return result;
    }

    /** 频道分类的固定顺序（与参考电视 UI 一致）。 */
    public static final String[] CATEGORY_ORDER = {
            "央视频道", "卫视频道", "电影频道", "数字频道",
            "儿童频道", "地方频道", "综艺频道", "纪录频道", "其他"
    };

    /** 把播单自带的 group 归一到固定分类；认不出来就按频道名猜（媒体库名字不参与分类）。 */
    private static String normalizeGroup(String group, String name) {
        String g = group == null ? "" : group.trim();
        if (g.length() == 0) {
            return guessGroup(name);
        }
        for (String category : CATEGORY_ORDER) {
            if (category.equals(g)) {
                return category;
            }
        }
        String up = g.toUpperCase(Locale.US);
        if (up.indexOf("央视") >= 0 || up.indexOf("CCTV") >= 0) {
            return "央视频道";
        }
        if (up.indexOf("卫视") >= 0) {
            return "卫视频道";
        }
        if (up.indexOf("电影") >= 0 || up.indexOf("影院") >= 0) {
            return "电影频道";
        }
        if (up.indexOf("数字") >= 0 || up.indexOf("4K") >= 0 || up.indexOf("8K") >= 0) {
            return "数字频道";
        }
        if (up.indexOf("少儿") >= 0 || up.indexOf("儿童") >= 0 || up.indexOf("卡通") >= 0) {
            return "儿童频道";
        }
        if (up.indexOf("地方") >= 0) {
            return "地方频道";
        }
        if (up.indexOf("综艺") >= 0 || up.indexOf("娱乐") >= 0) {
            return "综艺频道";
        }
        if (up.indexOf("纪录") >= 0 || up.indexOf("纪实") >= 0) {
            return "纪录频道";
        }
        return guessGroup(name);
    }

    /** 播单没写 group-title 时，按频道名推断分组（永远归到固定分类里）。 */
    private static String guessGroup(String name) {
        String n = name == null ? "" : name.toUpperCase(Locale.US);
        if (n.indexOf("CCTV") >= 0 || n.indexOf("央视") >= 0 || n.indexOf("CGTN") >= 0) {
            return "央视频道";
        }
        if (n.indexOf("卫视") >= 0) {
            return "卫视频道";
        }
        if (n.indexOf("电影") >= 0 || n.indexOf("影院") >= 0 || n.indexOf("CHC") >= 0
                || n.indexOf("MOVIE") >= 0 || n.indexOf("CINEMA") >= 0) {
            return "电影频道";
        }
        if (n.indexOf("4K") >= 0 || n.indexOf("8K") >= 0 || n.indexOf("UHD") >= 0
                || n.indexOf("数字") >= 0 || n.indexOf("高清") >= 0) {
            return "数字频道";
        }
        if (n.indexOf("少儿") >= 0 || n.indexOf("儿童") >= 0 || n.indexOf("卡通") >= 0
                || n.indexOf("动画") >= 0 || n.indexOf("KIDS") >= 0 || n.indexOf("BABY") >= 0) {
            return "儿童频道";
        }
        if (n.indexOf("综艺") >= 0 || n.indexOf("娱乐") >= 0 || n.indexOf("音乐") >= 0) {
            return "综艺频道";
        }
        if (n.indexOf("纪录") >= 0 || n.indexOf("纪实") >= 0 || n.indexOf("DOCUMENT") >= 0
                || n.indexOf("DISCOVERY") >= 0 || n.indexOf("NATIONAL GEOGRAPHIC") >= 0) {
            return "纪录频道";
        }
        if (n.indexOf("体育") >= 0 || n.indexOf("SPORT") >= 0) {
            return "综艺频道";
        }
        if (n.indexOf("TV") >= 0 || n.indexOf("频道") >= 0 || n.indexOf("CHANNEL") >= 0) {
            return "地方频道";
        }
        return "其他";
    }

    private static String extinfName(String line) {
        int comma = line.lastIndexOf(',');
        if (comma < 0 || comma + 1 >= line.length()) {
            return "";
        }
        return line.substring(comma + 1).trim();
    }

    private static String extinfAttr(String line, String key) {
        String marker = key + "=\"";
        int start = line.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int end = line.indexOf('"', start);
        return end > start ? line.substring(start, end) : "";
    }
}
