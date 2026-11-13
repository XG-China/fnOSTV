package com.fnostv.android4.net;

import com.fnostv.android4.config.ServerProfile;
import com.fnostv.android4.util.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class FnosRestClient {
    private static final int TIMEOUT_SECONDS = 15;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** 飞牛影视 web 客户端常量（与服务端 authx 签名校验一致）。 */
    private static final String API_V1 = "/v/api/v1";
    private static final String API_V2 = "/v/api/v2";

    /** 是否允许「原画直连」（电视芯片硬解）；由设置项同步过来，关掉则一律转码。 */
    public static volatile boolean directHardwareEnabled = true;

    /** 实际使用的媒体 API 前缀（不同 fnOS 版本 v1 / v2 只有一个可用）。 */
    private String apiBase = API_V1;
    private boolean apiBaseDetected;
    private static final String SIGN_SALT = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh";
    private static final String SIGN_API_KEY = "16CCEB3D-AB42-077D-36A1-F355324E4237";
    private static final String CLIENT_NAME = "web";
    private static final String CLIENT_VERSION = "631";
    private static final String LOGIN_PATH = "/user/loginByPassword";
    private static final String APP_NAME = "trimemedia-web";

    /** 排序字段（网页端 layout.list.sort.sortField.* 的接口取值，已对真实服务验证）。 */
    public static final String SORT_CREATE_TIME = "create_time";
    public static final String SORT_FAVORITE_TIME = "favorite_time";
    public static final String SORT_RELEASE_DATE = "release_date";
    public static final String SORT_TITLE = "sort_title";
    public static final String SORT_VOTE_AVERAGE = "vote_average";
    /** 演职人员作品页排序字段（网页端 /person/:guid 使用 update_time）。 */
    public static final String SORT_UPDATE_TIME = "update_time";
    /** 排序方向。 */
    public static final String SORT_ASC = "ASC";
    public static final String SORT_DESC = "DESC";

    /**
     * 服务器转码参数（官方 web 在浏览器不支持原视频流时走同一接口）：
     * 老设备（Android 4.4）无法硬解 4K/2K 原画，软解会占满 CPU 造成"卡成 PPT"，
     * 因此对高分辨率原画改用服务器实时转码的 1080P HLS 播放列表。
     */
    private static final String TRANSCODE_RESOLUTION = "1080";
    /** 转码默认码率（bps）：1080p 面板下 12Mbps 已经能保住细节。 */
    private static final int TRANSCODE_BITRATE = 12000000;
    /** 转码码率上限：4K 源按源码率自适应，但不超过 14Mbps（老盒子上 20Mbps 容易卡顿）。 */
    private static final int TRANSCODE_MAX_BITRATE = 14000000;
    /**
     * 转码必须指定 H.264：Android 4.4 上 VLC 走的是旧 IOMX 硬解通道，只认 H.264(AVC)，
     * HEVC 一律回退软解（实测 1080P HEVC 转码仍占 84% CPU 并丢帧，H.264 才是硬解）。
     */
    private static final String TRANSCODE_VIDEO_ENCODER = "h264";
    /** 转码音轨统一成 AAC 立体声：Android 4.4 无法输出 EAC3/DTS（原画常见音轨）。 */
    private static final String TRANSCODE_AUDIO_ENCODER = "aac";
    private static final int TRANSCODE_AUDIO_CHANNELS = 2;

    private static final Random NONCE_RANDOM = new Random();

    /** production_countries 的 ISO-3166-1 代码 → 中文名（与网页展示一致）。 */
    private static final java.util.Map<String, String> COUNTRY_NAMES = new java.util.HashMap<String, String>();

    static {
        String[][] pairs = {
                {"US", "美国"}, {"GB", "英国"}, {"CN", "中国大陆"}, {"HK", "中国香港"},
                {"TW", "中国台湾"}, {"JP", "日本"}, {"KR", "韩国"}, {"FR", "法国"},
                {"DE", "德国"}, {"IT", "意大利"}, {"ES", "西班牙"}, {"IN", "印度"},
                {"CA", "加拿大"}, {"AU", "澳大利亚"}, {"RU", "俄罗斯"}, {"BR", "巴西"},
                {"SE", "瑞典"}, {"NO", "挪威"}, {"DK", "丹麦"}, {"FI", "芬兰"},
                {"NL", "荷兰"}, {"BE", "比利时"}, {"CH", "瑞士"}, {"AT", "奥地利"},
                {"IE", "爱尔兰"}, {"NZ", "新西兰"}, {"SG", "新加坡"}, {"TH", "泰国"},
                {"MX", "墨西哥"}, {"AR", "阿根廷"}, {"PL", "波兰"}, {"CZ", "捷克"},
                {"PT", "葡萄牙"}, {"GR", "希腊"}, {"HU", "匈牙利"}, {"TR", "土耳其"},
                {"IL", "以色列"}, {"ZA", "南非"}, {"MY", "马来西亚"}, {"PH", "菲律宾"},
                {"ID", "印度尼西亚"}, {"UA", "乌克兰"}, {"RO", "罗马尼亚"},
        };
        for (int i = 0; i < pairs.length; i++) {
            COUNTRY_NAMES.put(pairs[i][0], pairs[i][1]);
        }
    }

    /** 画质徽标文案（网页 stream.video.videoResolution）。 */
    private static final java.util.Map<String, String> RESOLUTION_LABELS = new java.util.HashMap<String, String>();

    /** 色彩范围徽标文案（网页 stream.video.videoColorRange）。 */
    private static final java.util.Map<String, String> COLOR_RANGE_LABELS = new java.util.HashMap<String, String>();

    /** 音频规格徽标文案（网页 stream.audio.audioSpecs）。 */
    private static final java.util.Map<String, String> AUDIO_LABELS = new java.util.HashMap<String, String>();

    /** ISO 639 语言代码 → 中文语言名（网页语言选择器展示）。 */
    private static final java.util.Map<String, String> LANGUAGE_NAMES = new java.util.HashMap<String, String>();

    static {
        RESOLUTION_LABELS.put("480p", "480P");
        RESOLUTION_LABELS.put("720p", "720P");
        RESOLUTION_LABELS.put("1080p", "1080P");
        RESOLUTION_LABELS.put("4k", "4k");
        RESOLUTION_LABELS.put("8k", "8k");

        COLOR_RANGE_LABELS.put("DolbyVision", "杜比视界");
        COLOR_RANGE_LABELS.put("HDR10", "HDR10");
        COLOR_RANGE_LABELS.put("HLG", "HLG");
        COLOR_RANGE_LABELS.put("SDR", "SDR");
        COLOR_RANGE_LABELS.put("dolby", "杜比");

        AUDIO_LABELS.put("DolbySurround", "杜比环绕");
        AUDIO_LABELS.put("DolbyAtmos", "杜比全景声");
        AUDIO_LABELS.put("DTS", "DTS");
        AUDIO_LABELS.put("DTS-HD", "DTS-HD");
        AUDIO_LABELS.put("Stereo", "立体声");

        String[][] languages = {
                {"chi", "中文"}, {"zho", "中文"}, {"zh", "中文"}, {"cmn", "中文"},
                {"yue", "粤语"}, {"eng", "英语"}, {"en", "英语"}, {"jpn", "日语"}, {"ja", "日语"},
                {"kor", "韩语"}, {"ko", "韩语"}, {"fra", "法语"}, {"fre", "法语"}, {"fr", "法语"},
                {"deu", "德语"}, {"ger", "德语"}, {"de", "德语"}, {"spa", "西班牙语"}, {"es", "西班牙语"},
                {"ita", "意大利语"}, {"it", "意大利语"}, {"rus", "俄语"}, {"ru", "俄语"},
                {"por", "葡萄牙语"}, {"pt", "葡萄牙语"}, {"tha", "泰语"}, {"th", "泰语"},
                {"ara", "阿拉伯语"}, {"ar", "阿拉伯语"}, {"hin", "印地语"}, {"hi", "印地语"},
                {"vie", "越南语"}, {"vi", "越南语"}, {"nld", "荷兰语"}, {"dut", "荷兰语"},
                {"swe", "瑞典语"}, {"dan", "丹麦语"}, {"nor", "挪威语"}, {"fin", "芬兰语"},
                {"pol", "波兰语"}, {"tur", "土耳其语"}, {"ell", "希腊语"}, {"heb", "希伯来语"},
        };
        for (int i = 0; i < languages.length; i++) {
            LANGUAGE_NAMES.put(languages[i][0], languages[i][1]);
        }
    }

    private final ServerProfile profile;
    private MediaDetailInfoParser.GenreTable cachedGenreTable;
    private final OkHttpClient httpClient;
    private final File cacheDir;
    private String token;

    public FnosRestClient(ServerProfile profile) {
        this(profile, null);
    }

    public FnosRestClient(ServerProfile profile, File cacheDir) {
        this.profile = profile;
        this.cacheDir = cacheDir;
        this.httpClient = HttpClients.builder(profile.trustSslErrors)
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    public FnosMediaCounts mediaCounts() throws FnosRpcException {
        try {
            return parseMediaCounts(get("/mediadb/sum"));
        } catch (JSONException ex) {
            throw new FnosRpcException("解析影视统计失败", ex);
        }
    }

    public FnosFileList mediaLibraries() throws FnosRpcException {
        try {
            return parseMediaLibraries(get("/mediadb/list"));
        } catch (JSONException ex) {
            throw new FnosRpcException("解析媒体库列表失败", ex);
        }
    }

    public FnosFileList mediaItems(String ancestorGuid, String category, int pageSize) throws FnosRpcException {
        try {
            return parseMediaItems(ancestorGuid, post("/item/list", mediaItemPayload(ancestorGuid, category, pageSize)));
        } catch (JSONException ex) {
            throw new FnosRpcException("解析影视条目失败", ex);
        }
    }

    /**
     * 条目列表分页拉全：网页端一次展示整库，TV 端如果只拿第一页，大库（如 229 部电影）
     * 只能看到 page_size 个。逐页取到 total 或遇到不满页为止。
     */
    public FnosFileList mediaItemsAll(String ancestorGuid, String category, int pageSize) throws FnosRpcException {
        return mediaItemsAll(ancestorGuid, category, pageSize, SORT_CREATE_TIME, SORT_DESC);
    }

    /** 带排序字段的条目列表分页拉全（网页端排序工具条同款）。 */
    public FnosFileList mediaItemsAll(String ancestorGuid, String category, int pageSize,
                                      String sortColumn, String sortType) throws FnosRpcException {
        int size = Math.max(1, pageSize);
        List<FnosFileEntry> all = new ArrayList<FnosFileEntry>();
        try {
            int page = 1;
            while (true) {
                JSONObject response = post("/item/list",
                        mediaItemPayload(ancestorGuid, category, size, page, sortColumn, sortType));
                List<FnosFileEntry> entries = parseMediaItems(ancestorGuid, response).entries;
                if (entries.isEmpty() && page == 1 && ancestorGuid != null && ancestorGuid.length() > 0) {
                    // 有的 fnOS 版本对 tags.type / exclude_grouped_video 处理不同（会返回空列表），
                    // 退回最简请求体再试一次，避免「NAS 有片子、App 里是空的」。
                    Logger.w("item/list empty, retrying minimal payload ancestor=" + ancestorGuid
                            + " category=" + category);
                    JSONObject retry = post("/item/list",
                            mediaItemPayloadLoose(ancestorGuid, size, page, sortColumn, sortType));
                    List<FnosFileEntry> retryEntries = parseMediaItems(ancestorGuid, retry).entries;
                    if (!retryEntries.isEmpty()) {
                        response = retry;
                        entries = retryEntries;
                    } else {
                        Logger.w("item/list still empty, resp=" + truncate(retry.toString(), 400));
                    }
                }
                all.addAll(entries);
                int total = totalOf(response);
                if (entries.size() < size) {
                    break;
                }
                if (total >= 0 && all.size() >= total) {
                    break;
                }
                if (page >= 5000) {
                    Logger.w("item/list pagination stopped at page " + page);
                    break;
                }
                page++;
            }
            return new FnosFileList(ancestorGuid, all);
        } catch (JSONException ex) {
            throw new FnosRpcException("解析影视条目失败", ex);
        }
    }

    /**
     * 文件夹（网页 {@code /folder/:guid}）的直接子条目。
     *
     * <p><b>网页端同款请求体：</b>
     * <pre>{@code {"parent_guid":"<guid>","tags":{},"sort_type":"ASC","sort_column":"sort_title","page":1,"page_size":50}}</pre>
     *
     * <p>关键点：文件夹必须用 {@code parent_guid}（直接子级），不能用 {@code ancestor_guid}
     * （后者对文件夹恒返回 0 条，用户反馈「点进视频提示影视大全没进入真的视频页面」）；
     * 而且不能带 {@code exclude_grouped_video}——文件夹里的视频正是「成组视频」，
     * 带了这个参数服务端会把整组排除，列表就空了。
     */
    public FnosFileList folderItems(String parentGuid, int pageSize) throws FnosRpcException {
        return folderItems(parentGuid, pageSize, "all", SORT_TITLE, SORT_ASC);
    }

    /**
     * 文件夹列表（带排序与筛选，网页端文件夹页工具条同款）。
     *
     * @param sortColumn 排序字段，默认 sort_title（网页端「标题」）
     * @param sortType   排序方向，默认 ASC（网页端「升序」）
     * @param category   网页端「筛选」分类（all / movie / tv / live / other）
     */
    public FnosFileList folderItems(String parentGuid, int pageSize,
                                    String category, String sortColumn, String sortType) throws FnosRpcException {
        try {
            JSONObject response = post("/item/list",
                    folderItemPayload(parentGuid, pageSize, 1, category, sortColumn, sortType));
            return parseMediaItems(parentGuid, response);
        } catch (JSONException ex) {
            throw new FnosRpcException("解析文件夹条目失败", ex);
        }
    }

    /** 文件夹列表请求体（网页 /folder/:guid 同款）。 */
    public static String folderItemPayload(String parentGuid, int pageSize, int page) throws JSONException {
        return folderItemPayload(parentGuid, pageSize, page, "all", SORT_TITLE, SORT_ASC);
    }

    public static String folderItemPayload(String parentGuid, int pageSize, int page,
                                           String category, String sortColumn, String sortType) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("parent_guid", parentGuid == null ? "" : parentGuid);
        // 网页端文件夹页默认 tags 为空对象：不做类型过滤，文件夹内允许任意条目类型。
        // 选中「筛选」分类后才按 tags.type 过滤。
        JSONObject tags = new JSONObject();
        String normalized = category == null ? "" : category.trim().toLowerCase();
        if (normalized.length() > 0 && !"all".equals(normalized)) {
            tags.put("type", typeArray(category));
        }
        body.put("tags", tags);
        body.put("sort_type", sortType == null || sortType.length() == 0 ? SORT_ASC : sortType);
        body.put("sort_column", sortColumn == null || sortColumn.length() == 0 ? SORT_TITLE : sortColumn);
        body.put("page", Math.max(1, page));
        body.put("page_size", Math.max(1, pageSize));
        return body.toString();
    }

    /** GET /season/list/<tvGuid>：电视剧的季列表（网页同款）。 */
    public FnosFileList seasonItems(String tvGuid) throws FnosRpcException {
        JSONObject response = get("/season/list/" + encodeQueryValue(tvGuid));
        return new FnosFileList("season", parseEntries(dataArray(response)));
    }

    /** GET /episode/list/<seasonGuid>：某一季的剧集列表（网页同款）。 */
    public FnosFileList episodeItems(String seasonGuid) throws FnosRpcException {
        JSONObject response = get("/episode/list/" + encodeQueryValue(seasonGuid));
        return new FnosFileList("episode", parseEntries(dataArray(response)));
    }

    /**
     * 剧 / 季页面头部（网页 /tv/:guid、/tv/season/:guid 的 hero）。
     * 只请求 {@code GET /item/<guid>}，不额外拉演员与流。
     */
    public MediaSeriesHeader seriesHeader(String guid) throws FnosRpcException {
        String itemGuid = guid == null ? "" : guid.trim();
        if (itemGuid.length() == 0) {
            return MediaSeriesHeader.empty();
        }
        JSONObject response = get("/item/" + encodeQueryValue(itemGuid));
        JSONObject item = response.optJSONObject("data");
        if (item == null) {
            item = response;
        }
        return MediaSeriesHeader.fromItem(item, "");
    }

    /** 官方 web 详情：GET /v/api/v1/item/<guid>，data 直接就是 item 本体。 */
    public MediaDetailInfo itemDetail(String guid) throws FnosRpcException {
        String itemGuid = guid == null ? "" : guid.trim();
        if (itemGuid.length() == 0) {
            return MediaDetailInfo.empty();
        }
        JSONObject response = get("/item/" + encodeQueryValue(itemGuid));
        JSONObject item = response.optJSONObject("data");
        if (item == null) {
            item = response;
        }
        MediaDetailInfoParser.GenreTable genres = genreTable();
        MediaDetailInfo info = MediaDetailInfoParser.parse(item);
        List<MediaPerson> persons = personList(itemGuid);
        JSONObject streams = streamListOf(itemGuid);
        String contentRating = item.optString("content_ratings");
        String originalTitle = item.optString("original_title");
        // 网页详情头图取 item.backdrops，缺省时回退 item.posters（剧集只有横版 posters）。
        String backdrop = imagePath(item, "backdrops", "posters");
        String logo = imagePath(item, "logos");
        String libraryName = item.optString("ancestor_name");
        String country = countryOf(item.optJSONArray("production_countries"));
        JSONArray genreIds = item.optJSONArray("genres");
        return info.withScraped(
                originalTitle,
                genres.joinNames(genreIds),
                contentRating,
                country,
                libraryName,
                backdrop,
                logo,
                persons,
                formatVoteAverage(item.optString("vote_average")),
                techBadges(item.optJSONObject("media_stream"), streams),
                subtitleLabelOf(streams),
                audioLabelOf(streams));
    }

    /**
     * 取图片路径：兼容字符串 / 数组两种返回形态；按 keys 顺序取第一个非空值。
     * 网页详情头图为 {@code backdrops || posters}，因此把 posters 作为回退。
     */
    private static String imagePath(JSONObject item, String... keys) {
        if (item == null || keys == null) {
            return "";
        }
        for (int i = 0; i < keys.length; i++) {
            String path = firstImagePath(item.opt(keys[i]));
            if (path.length() > 0) {
                return path;
            }
        }
        return "";
    }

    private static String firstImagePath(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                String path = firstImagePath(array.opt(i));
                if (path.length() > 0) {
                    return path;
                }
            }
            return "";
        }
        String raw = value.toString().trim();
        if (raw.length() == 0 || "null".equals(raw)) {
            return "";
        }
        if (raw.charAt(0) == '[') {
            try {
                return firstImagePath(new JSONArray(raw));
            } catch (org.json.JSONException ex) {
                return "";
            } catch (RuntimeException ex) {
                return "";
            }
        }
        return raw;
    }

    /** GET /stream/list/<guid>：字幕 / 音轨语言与技术流信息（网页同款）。失败返回 null。 */
    private JSONObject streamListOf(String guid) {
        try {
            JSONObject response = get("/stream/list/" + encodeQueryValue(guid));
            JSONObject data = response.optJSONObject("data");
            return data == null ? response : data;
        } catch (FnosRpcException ex) {
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** 详情页"7.0 分"：vote_average 保留一位小数。 */
    private static String formatVoteAverage(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() == 0 || "null".equals(value)) {
            return "";
        }
        try {
            double number = Double.parseDouble(value);
            if (number <= 0d) {
                return "";
            }
            return String.format(java.util.Locale.US, "%.1f", number);
        } catch (NumberFormatException ex) {
            return value;
        }
    }

    /** 技术徽标：画质 / 色彩范围 / 音频规格，与网页三枚徽标图顺序一致。 */
    private static List<String> techBadges(JSONObject mediaStream, JSONObject streams) {
        List<String> badges = new ArrayList<String>();
        addMappedBadges(badges,
                mediaStream == null ? null : mediaStream.optJSONArray("resolutions"), RESOLUTION_LABELS);
        addMappedBadges(badges,
                mediaStream == null ? null : mediaStream.optJSONArray("color_range_type"), COLOR_RANGE_LABELS);
        String audioType = currentAudioType(streams);
        if (audioType.length() == 0 && mediaStream != null) {
            audioType = firstOf(mediaStream.optJSONArray("audio_type"));
        }
        String audioBadge = audioBadgeLabel(audioType);
        if (audioBadge.length() > 0) {
            badges.add(audioBadge);
        }
        return badges;
    }

    /** 网页 "中文字幕" / "无字幕"：取默认字幕轨语言；接口失败返回空串（由调用方回退）。 */
    private static String subtitleLabelOf(JSONObject streams) {
        if (streams == null) {
            return "";
        }
        JSONArray subtitles = streams.optJSONArray("subtitle_streams");
        if (subtitles == null || subtitles.length() == 0) {
            return "无字幕";
        }
        JSONObject stream = defaultStreamOf(subtitles);
        return languageLabel(stream == null ? "" : stream.optString("language")) + "字幕";
    }

    /** 网页 "中文音频" / "无音频"：取默认音轨语言；接口失败返回空串（由调用方回退）。 */
    private static String audioLabelOf(JSONObject streams) {
        if (streams == null) {
            return "";
        }
        JSONArray audios = streams.optJSONArray("audio_streams");
        if (audios == null || audios.length() == 0) {
            return "无音频";
        }
        JSONObject stream = defaultStreamOf(audios);
        return languageLabel(stream == null ? "" : stream.optString("language")) + "音频";
    }

    /** 默认流：is_default=1 的那条，否则第一条。 */
    private static JSONObject defaultStreamOf(JSONArray streams) {
        JSONObject first = null;
        for (int i = 0; i < streams.length(); i++) {
            JSONObject stream = streams.optJSONObject(i);
            if (stream == null) {
                continue;
            }
            if (first == null) {
                first = stream;
            }
            String isDefault = stream.optString("is_default");
            if ("1".equals(isDefault) || "true".equalsIgnoreCase(isDefault)) {
                return stream;
            }
        }
        return first;
    }

    /** 当前音轨规格：网页"当前音频规格：DTS"取当前音轨的 audio_type。 */
    private static String currentAudioType(JSONObject streams) {
        JSONArray audios = streams == null ? null : streams.optJSONArray("audio_streams");
        if (audios == null || audios.length() == 0) {
            return "";
        }
        JSONObject stream = defaultStreamOf(audios);
        return stream == null ? "" : stream.optString("audio_type");
    }

    private static void addMappedBadges(List<String> badges, JSONArray values, java.util.Map<String, String> labels) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.length(); i++) {
            String key = values.optString(i).trim();
            if (key.length() == 0) {
                continue;
            }
            String label = labels.get(key);
            if (label == null || label.length() == 0) {
                label = labels.get(key.toLowerCase(java.util.Locale.US));
            }
            if (label == null || label.length() == 0 || badges.contains(label)) {
                continue;
            }
            badges.add(label);
        }
    }

    private static String firstOf(JSONArray array) {
        if (array == null || array.length() == 0) {
            return "";
        }
        String value = array.optString(0);
        return value == null ? "" : value.trim();
    }

    /** 音频徽标文案：Others 无徽标图，网页对普通立体声也不出徽标。 */
    private static String audioBadgeLabel(String audioType) {
        String value = audioType == null ? "" : audioType.trim();
        if (value.length() == 0 || "Others".equals(value) || "Stereo".equals(value)) {
            return "";
        }
        String label = AUDIO_LABELS.get(value);
        return label == null ? "" : label;
    }

    /** ISO 语言代码 → 网页语言名（chi/zh → 中文，und 或空 → 未知）。 */
    private static String languageLabel(String raw) {
        String code = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.US);
        if (code.length() == 0 || "und".equals(code) || "unknown".equals(code)) {
            return "未知";
        }
        String label = LANGUAGE_NAMES.get(code);
        if (label != null) {
            return label;
        }
        int dash = code.indexOf('-');
        if (dash > 0) {
            label = LANGUAGE_NAMES.get(code.substring(0, dash));
            if (label != null) {
                return label;
            }
        }
        return raw.trim();
    }

    private static String countryOf(JSONArray countries) {
        if (countries == null || countries.length() == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < countries.length(); i++) {
            String code = countries.optString(i, "");
            String name = COUNTRY_NAMES.get(code.toUpperCase());
            String value = name.length() > 0 ? name : code;
            if (value.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    /** 演职人员：POST /v/api/v1/person/list/<guid>，按 order 取导演与演员。 */
    public List<MediaPerson> personList(String guid) throws FnosRpcException {
        JSONObject body = new JSONObject();
        try {
            body.put("page", 1);
            body.put("page_size", 200);
        } catch (JSONException ignored) {
        }
        JSONObject response = post("/person/list/" + encodeQueryValue(guid), body.toString());
        List<MediaPerson> persons = new ArrayList<MediaPerson>();
        JSONObject data = response.optJSONObject("data");
        JSONArray list = data == null ? null : data.optJSONArray("list");
        if (list == null) {
            return persons;
        }
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String name = firstNonEmpty(item.optString("name"), item.optString("original_name"));
            if (name.length() == 0) {
                continue;
            }
            String job = item.optString("job");
            String role = item.optString("role");
            String label;
            if ("Director".equalsIgnoreCase(job)) {
                label = "导演";
            } else if (role.length() > 0) {
                label = "饰 " + role;
            } else {
                label = job.length() > 0 ? job : "";
            }
            persons.add(new MediaPerson(name, label, item.optString("profile_path"),
                    item.optInt("order", i + 1), item.optString("person_guid")));
        }
        return persons;
    }

    /** 类型 id→名称映射：GET /v/api/v1/tag/genres?lan=zh-CN，内存缓存。 */
    public MediaDetailInfoParser.GenreTable genreTable() throws FnosRpcException {
        if (cachedGenreTable != null) {
            return cachedGenreTable;
        }
        JSONObject response = get("/tag/genres?lan=zh-CN");
        JSONArray data = response.optJSONArray("data");
        MediaDetailInfoParser.GenreTable table = new MediaDetailInfoParser.GenreTable();
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item != null) {
                    table.put(item.optInt("id"), item.optString("value"));
                }
            }
        }
        cachedGenreTable = table;
        return table;
    }

    /**
     * 服务器转码会话（HLS）。官方 web 在这个会话上每隔十几秒上报一次播放进度
     * （{@code POST /play/record} 带 play_link），转码会话正是靠这条上报保活：
     * 实测既不刷新播放列表、也不带 Play-Link 头都无效，会话建立约 60 秒后
     * 所有分片都会 410，只有周期性的 /play/record 能让它一直可用。
     */
    public static final class TranscodeSession {
        public final String url;
        public final String itemGuid;
        public final String mediaGuid;
        public final String videoGuid;
        public final String audioGuid;
        public final String playLink;
        public final String resolution;
        public final int bitrate;
        public final long durationSeconds;
        /** 会话建立时刻：服务器转码会话约 60 秒不用就失效，详情页预解析出来的会话可能已过期。 */
        public final long createdAtMs;

        TranscodeSession(String url, String itemGuid, String mediaGuid, String videoGuid, String audioGuid,
                         String playLink, String resolution, int bitrate, long durationSeconds) {
            this.url = url == null ? "" : url;
            this.itemGuid = itemGuid == null ? "" : itemGuid;
            this.mediaGuid = mediaGuid == null ? "" : mediaGuid;
            this.videoGuid = videoGuid == null ? "" : videoGuid;
            this.audioGuid = audioGuid == null ? "" : audioGuid;
            this.playLink = playLink == null ? "" : playLink;
            this.resolution = resolution == null ? "" : resolution;
            this.bitrate = bitrate;
            this.durationSeconds = durationSeconds < 0 ? 0L : durationSeconds;
            this.createdAtMs = System.currentTimeMillis();
        }

        /**
         * 直连（原画）播放的进度上报载体：没有 HLS 会话，只需要 item/media guid，
         * 播放期间用它调用 {@link #reportPlaybackProgress} 回写观看进度
         * （官方 web 的 addRecord 同款，直连播放同样需要记录进度）。
         */
        static TranscodeSession forDirectProgress(String itemGuid, String mediaGuid, long durationSeconds) {
            return new TranscodeSession("", itemGuid, mediaGuid, "", "", "", "", 0, durationSeconds);
        }
    }

    /** 播放直链 + 观看进度（秒），供详情页进度条与"继续播放"使用。 */
    public static final class PlaybackDirect {
        public final String url;
        public final long positionSeconds;
        public final long durationSeconds;
        public final boolean hasSubtitle;
        public final String label;
        /** 非空时播放期间需要周期性调用 {@link #reportPlaybackProgress}：
         *  转码会话靠它保活；直连播放靠它 + {@code forDirectProgress()} 回写观看进度。 */
        public final TranscodeSession transcode;

        public PlaybackDirect(String url, long positionSeconds, long durationSeconds, boolean hasSubtitle) {
            this(url, positionSeconds, durationSeconds, hasSubtitle, "原画");
        }

        public PlaybackDirect(String url, long positionSeconds, long durationSeconds, boolean hasSubtitle, String label) {
            this(url, positionSeconds, durationSeconds, hasSubtitle, label, null);
        }

        public PlaybackDirect(String url, long positionSeconds, long durationSeconds, boolean hasSubtitle, String label,
                              TranscodeSession transcode) {
            this.url = url == null ? "" : url;
            this.positionSeconds = positionSeconds;
            this.durationSeconds = durationSeconds;
            this.hasSubtitle = hasSubtitle;
            this.label = label == null || label.length() == 0 ? "原画" : label;
            this.transcode = transcode;
        }
    }

    /**
     * 播放直链（官方 web 同款）：POST /play/info 拿 media_guid，
     * 再拼 GET /media/range/<media_guid>?authorization=<token>，无需 WebSocket RPC。
     *
     * <p>当原画为 2K/4K 及以上时，Android 4.4 老电视盒无法硬件解码（软解 CPU 满载 → 卡顿），
     * 与网页版一致改用服务器转码：POST /play/play 拿 HLS 播放列表（1080P）后再播放。</p>
     */
    public PlaybackDirect playbackDirectForItem(String guid) throws FnosRpcException {
        String itemGuid = guid == null ? "" : guid.trim();
        if (itemGuid.length() == 0) {
            throw new FnosRpcException("缺少条目 guid");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("item_guid", itemGuid);
        } catch (JSONException ignored) {
        }
        JSONObject response = post("/play/info", body.toString());
        JSONObject data = response.optJSONObject("data");
        String mediaGuid = data == null ? "" : data.optString("media_guid");
        if (mediaGuid.length() == 0) {
            throw new FnosRpcException("play/info 未返回 media_guid");
        }
        long position = data.optLong("ts", 0L);
        if (position < 0) {
            position = 0L;
        }
        JSONObject item = data.optJSONObject("item");
        long duration = item == null ? 0L : item.optLong("duration", 0L);
        if (duration > 24L * 60L * 60L) {
            duration = duration / 1000L;
        }
        // 已经看到结尾（或差 30 秒以内）的片子：从头播，否则一点就播完退出
        if (duration > 0 && position > 0 && position >= duration - 30) {
            position = 0L;
        }
        boolean hasSubtitle = data.optString("subtitle_guid").length() > 0;
        ensureToken();
        // 原画无法硬解（非 H.264 或 2K/4K）→ 服务器转码 1080P H.264（唯一可行的流畅方案）。
        StreamInfo streams = streamInfoOf(itemGuid);
        if (needsTranscode(item, streams)) {
            // 未刮削的"真实文件"条目 /play/info 不给 video_guid/audio_guid，退回 /stream/list 里的流 guid。
            String videoGuid = data.optString("video_guid");
            if (videoGuid.length() == 0) {
                videoGuid = streams == null ? "" : streams.videoGuid;
            }
            String audioGuid = data.optString("audio_guid");
            if (audioGuid.length() == 0) {
                audioGuid = streams == null ? "" : streams.audioGuid;
            }
            TranscodeSession session = transcodePlaylist(itemGuid, mediaGuid, videoGuid, audioGuid,
                    duration, transcodeBitrateFor(streams));
            if (session != null && session.url.length() > 0) {
                return new PlaybackDirect(session.url, position, duration, hasSubtitle,
                        TRANSCODE_RESOLUTION + "P 转码", session);
            }
            Logger.w("Transcode unavailable, fallback to original stream media=" + mediaGuid);
        }
        String url = profile.baseUrl + "/v/api/v1/media/range/" + mediaGuid + "?authorization=" + encodeQueryValue(token);
        // 直连播放同样要回写观看进度（网页 addRecord），否则"继续观看"一直停在 0。
        return new PlaybackDirect(url, position, duration, hasSubtitle, "原画",
                TranscodeSession.forDirectProgress(itemGuid, mediaGuid, duration));
    }

    /** 原画的技术流概要：GET /stream/list/<item_guid> 的默认视频/音频流，失败返回 null。 */
    private StreamInfo streamInfoOf(String itemGuid) {
        JSONObject streams = streamListOf(itemGuid);
        if (streams == null) {
            return null;
        }
        JSONArray videos = streams.optJSONArray("video_streams");
        JSONObject video = videos == null ? null : defaultStreamOf(videos);
        JSONArray audios = streams.optJSONArray("audio_streams");
        JSONObject audio = audios == null ? null : defaultStreamOf(audios);
        if (video == null && audio == null) {
            return null;
        }
        return new StreamInfo(
                video == null ? "" : video.optString("codec_name"),
                video == null ? "" : video.optString("resolution_type"),
                audio == null ? "" : audio.optString("codec_name"),
                audio == null ? 0 : audio.optInt("channels", 0),
                video == null ? "" : video.optString("guid"),
                audio == null ? "" : audio.optString("guid"),
                video == null ? 0L : bitRateOf(video),
                video == null ? 0 : video.optInt("bit_depth", 0),
                video == null ? "" : video.optString("wrapper"),
                video == null ? 0 : video.optInt("width", 0),
                video == null ? 0 : video.optInt("height", 0),
                video == null ? 1 : video.optInt("progressive", 1));
    }

    /** 视频流码率（bps）：bit_rate 可能是数字也可能是字符串。 */
    private static long bitRateOf(JSONObject video) {
        Object raw = video.opt("bit_rate");
        if (raw == null) {
            raw = video.opt("bitrate");
        }
        if (raw == null) {
            return 0L;
        }
        if (raw instanceof Number) {
            return Math.max(0L, ((Number) raw).longValue());
        }
        try {
            return Math.max(0L, Long.parseLong(String.valueOf(raw).trim()));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    /** 转码码率：源码率更高就跟随源（上限 20Mbps），否则用默认值。 */
    private static int transcodeBitrateFor(StreamInfo streams) {
        long source = streams == null ? 0L : streams.bitRate;
        if (source > TRANSCODE_BITRATE) {
            return (int) Math.min(source, TRANSCODE_MAX_BITRATE);
        }
        return TRANSCODE_BITRATE;
    }

    /** 视频/音频技术流概要。 */
    private static final class StreamInfo {
        final String videoCodec;
        final String resolutionType;
        final String audioCodec;
        final int audioChannels;
        /**
         * 技术流自身的 guid。<b>未刮削的"真实文件"条目</b>（/item/list 里 type=Video、没有刮削信息）
         * 在 /play/info 里 video_guid/audio_guid 是空串，转码请求会因缺 guid 直接失败；
         * 而 /stream/list 的 video_streams/audio_streams 里始终带着可用的 guid，
         * 取来即可让这类文件也能走服务器转码（否则只能软解 HEVC 卡顿）。
         */
        final String videoGuid;
        final String audioGuid;
        /** 源视频码率（bps），取不到为 0。 */
        final long bitRate;
        /** 视频位深（8/10/12），取不到为 0。 */
        final int bitDepth;
        /** 容器：MP4 / MKV / TS…（服务端 wrapper 字段）。 */
        final String wrapper;
        final int width;
        final int height;
        /** 1=逐行，0=隔行（1080i 需要反交错，直连容易花屏/卡顿）。 */
        final int progressive;

        StreamInfo(String videoCodec, String resolutionType, String audioCodec, int audioChannels,
                   String videoGuid, String audioGuid, long bitRate, int bitDepth,
                   String wrapper, int width, int height, int progressive) {
            this.videoCodec = videoCodec == null ? "" : videoCodec.trim().toLowerCase(java.util.Locale.US);
            this.resolutionType = resolutionType == null ? "" : resolutionType.trim().toLowerCase(java.util.Locale.US);
            this.audioCodec = audioCodec == null ? "" : audioCodec.trim().toLowerCase(java.util.Locale.US);
            this.audioChannels = audioChannels;
            this.videoGuid = videoGuid == null ? "" : videoGuid.trim();
            this.audioGuid = audioGuid == null ? "" : audioGuid.trim();
            this.bitRate = bitRate;
            this.bitDepth = bitDepth;
            this.wrapper = wrapper == null ? "" : wrapper.trim().toLowerCase(java.util.Locale.US);
            this.width = width;
            this.height = height;
            this.progressive = progressive;
        }

        /** 老设备（Android 4.4 / VLC 3.x）走的是旧 IOMX 硬解通道，只认 H.264(AVC)。 */
        boolean isHardwareDecodable() {
            return "h264".equals(videoCodec) || "avc".equals(videoCodec) || "h.264".equals(videoCodec)
                    || "hevc".equals(videoCodec) || "h265".equals(videoCodec) || "h.265".equals(videoCodec)
                    || "hevc_main".equals(videoCodec);
        }

        /**
         * 音轨能否直接出声。
         *
         * <p>直连时走的是电视系统播放器（MTK 音视频解码 + 电视扬声器），
         * DDP/EAC3/AC3 这些电视芯片本身支持（实测 DDP 5.1 能正常播放），
         * 但 DTS/TrueHD 这类电视普遍不解，仍然交给服务器转码。
         */
        boolean isAudioDirectPlayable() {
            if (audioCodec.length() == 0) {
                return true;
            }
            if (audioCodec.indexOf("dts") >= 0 || audioCodec.indexOf("truehd") >= 0
                    || audioCodec.indexOf("mlp") >= 0) {
                return false;
            }
            return audioChannels <= 6;
        }
    }

    /**
     * 是否需要服务器转码。判据来自 {@code GET /stream/list/<item_guid>}：
     *
     * <ul>
     *   <li>视频编码不是 H.264：HEVC/AV1/VP9/RealVideo…在旧 IOMX 通道上没有硬解，100% 软解，
     *       1080p 就会 CPU 满载掉帧（"卡成PPT"）；</li>
     *   <li>H.264 但达到 2K/4K：分辨率超出硬解能力，同样只能软解；</li>
     *   <li>音轨不是设备能直接出声的格式/声道数（DTS/AC3/EAC3、或多声道 PCM）。</li>
     * </ul>
     *
     * <p>取不到技术流信息时退回"2K/4K 才转码"的保守判断，避免把能直连的片子误转码。</p>
     */
    private static boolean needsTranscode(JSONObject item, StreamInfo streams) {
        // 设置里关掉「原画直连」时，一律回到服务器转码（转码流走 VLC，最稳）
        if (!directHardwareEnabled) {
            return true;
        }
        if (streams != null && (streams.videoCodec.length() > 0 || streams.audioCodec.length() > 0)) {
            boolean hardware = streams.isHardwareDecodable();
            boolean audioOk = streams.isAudioDirectPlayable();
            // 高分辨率不再强制转码：电视的 MTK 播放器实测能吃 4K H.264 / 4K HEVC(MKV) 原画。
            // 但 10bit 及以上的 HEVC 还没验证过，保守起见继续交给服务器转码。
            boolean deepColor = streams.bitDepth > 8;
            // 实测：4K HEVC 的 MP4（hvc1）会让这台 MTK 电视直接重启（系统播放器一启动就崩），
            // 《安全警长啦咘啦哆》就是这种文件，必须转码。
            boolean hevc = streams.videoCodec.indexOf("hevc") >= 0
                    || streams.videoCodec.indexOf("h265") >= 0
                    || streams.videoCodec.indexOf("h.265") >= 0;
            boolean mp4 = streams.wrapper.indexOf("mp4") >= 0 || streams.wrapper.indexOf("mov") >= 0;
            boolean huge = streams.width >= 2560 || streams.height >= 1440
                    || isHighResolution(streams.resolutionType);
            boolean hevcMp4Huge = hevc && mp4 && huge;
            // 1080p 蓝光 remux 常见 25–40 Mbps：电视走 WiFi 直连容易卡住/变幻灯片，改走转码更稳
            boolean heavyBitrate = streams.bitRate > 20000000L;
            // 1080i 隔行片源需要反交错，直连会花屏/卡顿
            boolean interlaced = streams.progressive == 0;
            return !hardware || !audioOk || deepColor || hevcMp4Huge
                    || heavyBitrate || interlaced;
        }
        return needsTranscodeByResolution(item);
    }

    /** 拿不到技术流信息时的退回判断：原画达到 2K/4K 就转码。 */
    private static boolean needsTranscodeByResolution(JSONObject item) {
        if (item == null) {
            return false;
        }
        JSONObject mediaStream = item.optJSONObject("media_stream");
        JSONArray resolutions = mediaStream == null ? null : mediaStream.optJSONArray("resolutions");
        if (resolutions == null) {
            return false;
        }
        for (int i = 0; i < resolutions.length(); i++) {
            if (isHighResolution(resolutions.optString(i))) {
                return true;
            }
        }
        return false;
    }

    /** 分辨率标识（"2160p"/"4k"/"1080p"）是否达到 2K（1440p）及以上。 */
    private static boolean isHighResolution(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(java.util.Locale.US);
        if (text.length() == 0) {
            return false;
        }
        if (text.indexOf("4k") >= 0 || text.indexOf("8k") >= 0 || text.indexOf("2k") >= 0) {
            return true;
        }
        return leadingNumber(text) >= 1440;
    }

    /** 取字符串开头的数字（"2160p" → 2160），无数字返回 0。 */
    private static int leadingNumber(String value) {
        int index = 0;
        while (index < value.length() && !Character.isDigit(value.charAt(index))) {
            index++;
        }
        int start = index;
        while (index < value.length() && Character.isDigit(value.charAt(index))) {
            index++;
        }
        if (index <= start) {
            return 0;
        }
        try {
            return Integer.parseInt(value.substring(start, index));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * POST /play/play 取服务器转码的 HLS 播放列表，并把相对分片地址改写成
     * 带 authorization 的绝对地址后落盘（VLC 解析相对地址时会丢掉查询串，
     * 分片请求会因此鉴权失败，所以必须提前改写）。
     *
     * <p>音频一并转成 AAC 立体声：原画常见 EAC3/DTS，Android 4.4 的 AudioTrack
     * 无法输出（VLC 日志 failed to create audio output），网页版在浏览器不支持
     * 原音轨时也是这么转的。</p>
     *
     * @return 转码会话，失败返回 null。
     */
    private TranscodeSession transcodePlaylist(String itemGuid, String mediaGuid, String videoGuid, String audioGuid,
                                               long durationSeconds, int bitrate) {
        if (videoGuid == null || videoGuid.length() == 0) {
            Logger.w("Transcode skipped: no video stream guid item=" + itemGuid);
            return null;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("media_guid", mediaGuid);
            body.put("video_guid", videoGuid);
            body.put("video_encoder", TRANSCODE_VIDEO_ENCODER);
            body.put("resolution", TRANSCODE_RESOLUTION);
            body.put("bitrate", bitrate);
            body.put("startTimestamp", 0);
            if (audioGuid != null && audioGuid.length() > 0) {
                body.put("audio_guid", audioGuid);
                body.put("audio_encoder", TRANSCODE_AUDIO_ENCODER);
                body.put("channels", TRANSCODE_AUDIO_CHANNELS);
            }
            JSONObject response = post("/play/play", body.toString());
            JSONObject data = response.optJSONObject("data");
            String playLink = data == null ? "" : data.optString("play_link");
            if (playLink.length() == 0) {
                return null;
            }
            String playlistUrl = playLink.startsWith("http") ? playLink : profile.baseUrl + playLink;
            String playlist = fetchText(playlistUrl);
            if (playlist.length() == 0) {
                return null;
            }
            String local = writeLocalPlaylist(mediaGuid, playlistUrl, playlist);
            if (local.length() == 0) {
                return null;
            }
            return new TranscodeSession(local, itemGuid, mediaGuid, videoGuid, audioGuid,
                    playLink, TRANSCODE_RESOLUTION, bitrate, durationSeconds);
        } catch (FnosRpcException ex) {
            Logger.w("Transcode play/play failed: " + ex.getMessage());
        } catch (JSONException ex) {
            Logger.w("Transcode payload failed: " + ex.getMessage());
        } catch (RuntimeException ex) {
            Logger.w("Transcode crashed: " + ex.getMessage());
        }
        return null;
    }

    /**
     * 上报播放进度（官方 web 的 /play/record）。对转码会话这条请求同时起保活作用，
     * 播放期间必须周期性调用（网页约每 12 秒一次），否则会话建立约 60 秒后失效。
     *
     * <p>直连（原画）播放没有 play_link，但同样要按网页的 addRecord 上报
     * {@code item_guid/media_guid/ts}，否则"继续观看"永远停在 0（实测缺 media_guid 会
     * 返回 Invalid Params，只有 ts 是不够的）。</p>
     *
     * @return 上报是否成功。
     */
    public boolean reportPlaybackProgress(TranscodeSession session, long positionSeconds, long durationSeconds,
                                          String subtitleGuid) {
        if (session == null || session.itemGuid.length() == 0 || session.mediaGuid.length() == 0) {
            return false;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("item_guid", session.itemGuid);
            body.put("media_guid", session.mediaGuid);
            if (session.playLink.length() > 0) {
                body.put("video_guid", session.videoGuid);
                body.put("audio_guid", session.audioGuid);
                body.put("subtitle_guid", subtitleGuid == null ? "" : subtitleGuid);
                body.put("resolution", session.resolution);
                body.put("bitrate", session.bitrate);
            }
            body.put("ts", Math.max(0L, positionSeconds));
            body.put("duration", Math.max(0L, durationSeconds));
            if (session.playLink.length() > 0) {
                body.put("play_link", session.playLink);
            }
            post("/play/record", body.toString());
            return true;
        } catch (FnosRpcException ex) {
            Logger.w("Play/record failed: " + ex.getMessage());
        } catch (JSONException ex) {
            Logger.w("Play/record payload failed: " + ex.getMessage());
        }
        return false;
    }

    /** 带 token 拉取 m3u8 文本（HLS 播放列表与分片都只认 Authorization 头）。 */
    private String fetchText(String url) throws FnosRpcException {
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .header("Accept", "*/*")
                .build();
        Response response = null;
        try {
            response = httpClient.newCall(request).execute();
            if (!response.isSuccessful() || response.body() == null) {
                throw new FnosRpcException("HLS 播放列表 HTTP " + response.code());
            }
            return response.body().string();
        } catch (IOException ex) {
            throw new FnosRpcException("HLS 播放列表请求失败", ex);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /** 把 m3u8 里的相对分片地址改写成「绝对地址 + authorization」并写入缓存目录。 */
    /** 刷新后的列表：本地地址 + 服务器已经转码到的时长。 */
    public static final class PlaylistRefresh {
        public final String url;
        public final int headMs;

        PlaylistRefresh(String url, int headMs) {
            this.url = url == null ? "" : url;
            this.headMs = Math.max(0, headMs);
        }
    }

    /** 只保留最近 4 个 HLS 列表，避免缓存目录无限增长。 */
    private static void pruneOldPlaylists(File dir, File keep) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= 4) {
            return;
        }
        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                long a = left.lastModified();
                long b = right.lastModified();
                return a == b ? 0 : (a < b ? 1 : -1);
            }
        });
        for (int i = 4; i < files.length; i++) {
            if (!files[i].equals(keep)) {
                files[i].delete();
            }
        }
    }

    /**
     * 重新拉取同一个转码会话的 HLS 列表并覆盖本地缓存。
     *
     * <p>服务器边转边出片（实测约 8 倍速），但本地缓存的是播放开始那一刻的列表快照；
     * 拖动前重新拉一次，能拿到新生成的分片，同时算出「目前最远能跳到哪」。
     */
    public PlaylistRefresh refreshTranscodePlaylist(TranscodeSession session) {
        if (session == null || session.playLink.length() == 0) {
            return new PlaylistRefresh("", 0);
        }
        try {
            String playlistUrl = session.playLink.startsWith("http")
                    ? session.playLink : profile.baseUrl + session.playLink;
            String playlist = fetchText(playlistUrl);
            if (playlist.length() == 0) {
                return new PlaylistRefresh("", 0);
            }
            String local = writeLocalPlaylist(session.mediaGuid, playlistUrl, playlist);
            return new PlaylistRefresh(local, playlistDurationMs(playlist));
        } catch (FnosRpcException ex) {
            Logger.w("HLS playlist refresh failed: " + ex.getMessage());
            return new PlaylistRefresh("", 0);
        } catch (RuntimeException ex) {
            Logger.w("HLS playlist refresh crashed: " + ex.getMessage());
            return new PlaylistRefresh("", 0);
        }
    }

    /** HLS 列表里所有分片时长之和（毫秒）＝ 服务器已经转好的时长。 */
    private static int playlistDurationMs(String playlist) {
        if (playlist == null || playlist.length() == 0) {
            return 0;
        }
        double seconds = 0;
        String[] lines = playlist.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.startsWith("#EXTINF:")) {
                continue;
            }
            int comma = line.indexOf(',');
            String value = line.substring(7, comma > 7 ? comma : line.length()).trim();
            try {
                seconds += Double.parseDouble(value);
            } catch (NumberFormatException ignored) {
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, seconds * 1000);
    }

    private String writeLocalPlaylist(String mediaGuid, String playlistUrl, String playlist) {
        if (cacheDir == null) {
            return "";
        }
        try {
            File dir = new File(cacheDir, "hls");
            if (!dir.exists() && !dir.mkdirs()) {
                return "";
            }
            String base = playlistUrl.substring(0, playlistUrl.lastIndexOf('/') + 1);
            String suffix = "?authorization=" + encodeQueryValue(token);
            String[] lines = playlist.split("\n", -1);
            StringBuilder out = new StringBuilder(playlist.length() + 1024);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                String trimmed = line.trim();
                if (trimmed.length() == 0 || trimmed.charAt(0) == '#') {
                    out.append(line).append('\n');
                    continue;
                }
                out.append(base).append(trimmed).append(suffix).append('\n');
            }
            // 每次用新文件名：拖动时会刷新列表，如果覆盖旧文件名，
            // 仍在播放的播放器会读到写了一半的文件（画面花屏/卡死）。
            String name = (mediaGuid.length() == 0 ? "stream" : mediaGuid)
                    + "-" + System.currentTimeMillis() + ".m3u8";
            File file = new File(dir, name);
            FileWriter writer = new FileWriter(file, false);
            try {
                writer.write(out.toString());
            } finally {
                writer.close();
            }
            pruneOldPlaylists(dir, file);
            // 用 file:// 让 VLC 的 HLS(adaptive) 模块按本地播放列表打开，
            // 列表里的分片再走带 authorization 的绝对地址。
            return "file://" + file.getAbsolutePath();
        } catch (IOException ex) {
            Logger.w("HLS playlist cache failed: " + ex.getMessage());
        } catch (RuntimeException ex) {
            Logger.w("HLS playlist cache crashed: " + ex.getMessage());
        }
        return "";
    }

    /** 收藏同样分页拉全（网页端一次展示全部收藏）。 */
    public FnosFileList favoriteItems() throws FnosRpcException {
        return favoriteItems(null, SORT_CREATE_TIME, SORT_DESC);
    }

    /**
     * 收藏列表（网页端 /favorite 的分页拉全）。
     *
     * @param type       收藏分类，对应网页端 favoriteTabs：Movie / TV / Episode / LiveChannel / Person，null 表示全部
     * @param sortColumn 排序字段，同 item/list
     * @param sortType   排序方向
     */
    public FnosFileList favoriteItems(String type, String sortColumn, String sortType) throws FnosRpcException {
        List<FnosFileEntry> all = new ArrayList<FnosFileEntry>();
        try {
            int page = 1;
            while (true) {
                JSONObject body = new JSONObject();
                JSONObject tags = new JSONObject();
                if (type != null && type.length() > 0) {
                    JSONArray types = new JSONArray();
                    types.put(type);
                    tags.put("type", types);
                }
                body.put("tags", tags);
                body.put("sort_type", sortType == null || sortType.length() == 0 ? SORT_DESC : sortType);
                body.put("sort_column", sortColumn == null || sortColumn.length() == 0 ? SORT_CREATE_TIME : sortColumn);
                body.put("page", page);
                body.put("page_size", 50);
                FnosFileList one = parseMediaItems("favorite", post("/favorite/list", body.toString()));
                all.addAll(one.entries);
                if (one.entries.size() < 50 || page >= 1000) {
                    break;
                }
                page++;
            }
            return new FnosFileList("favorite", all);
        } catch (JSONException ex) {
            throw new FnosRpcException("构造收藏列表请求失败", ex);
        }
    }

    /**
     * 关键词搜索：GET /search/list?q=xxx。
     * 服务端返回的是裸数组（data 为数组，不是 {list:[]}），与 item/list 的结构不同。
     */
    public FnosFileList searchItems(String keyword) throws FnosRpcException {
        String query = keyword == null ? "" : keyword.trim();
        if (query.length() == 0) {
            return new FnosFileList("search", new ArrayList<FnosFileEntry>());
        }
        JSONObject response = get("/search/list?q=" + encodeQueryValue(query));
        return new FnosFileList("search", parseEntries(dataArray(response)));
    }

    /** 演职人员作品列表：POST /person/item/list（网页端 /person/:guid 页面）。 */
    public FnosFileList personItems(String personGuid) throws FnosRpcException {
        if (personGuid == null || personGuid.length() == 0) {
            return new FnosFileList("person", new ArrayList<FnosFileEntry>());
        }
        try {
            JSONObject body = new JSONObject();
            body.put("person_guid", personGuid);
            body.put("page", 1);
            body.put("page_size", 200);
            body.put("job", "");
            body.put("sort_column", SORT_UPDATE_TIME);
            body.put("sort_type", "desc");
            JSONObject response = post("/person/item/list", body.toString());
            JSONObject data = response.optJSONObject("data");
            JSONArray list = data == null ? null : data.optJSONArray("list");
            return new FnosFileList("person", parseEntries(list));
        } catch (JSONException ex) {
            throw new FnosRpcException("解析演职人员作品失败", ex);
        }
    }

    public FnosFileList recentItems() throws FnosRpcException {
        try {
            return parsePlayList(get("/play/list"));
        } catch (JSONException ex) {
            throw new FnosRpcException("解析继续观看列表失败", ex);
        }
    }

    public JSONObject version() throws FnosRpcException {
        return get("/sys/version");
    }

    public JSONObject userInfo() throws FnosRpcException {
        return get("/user/info");
    }

    public String authorizationToken() {
        return token == null ? "" : token;
    }

    public JSONArray managerUsers() throws FnosRpcException {
        return dataArray(get("/manager/user/list"));
    }

    public JSONArray taskSchedules() throws FnosRpcException {
        return dataArray(get("/task/schedule/list"));
    }

    public JSONArray gpuList() throws FnosRpcException {
        return dataArray(get("/server/gpu/list"));
    }

    public static String mediaItemPayload(String ancestorGuid, String category, int pageSize) throws JSONException {
        return mediaItemPayload(ancestorGuid, category, pageSize, 1);
    }

    public static String mediaItemPayload(String ancestorGuid, String category, int pageSize, int page) throws JSONException {
        return mediaItemPayload(ancestorGuid, category, pageSize, page, SORT_CREATE_TIME, SORT_DESC);
    }

    /**
     * 条目列表请求体（网页端同款）。
     *
     * @param sortColumn 排序字段：create_time / favorite_time / release_date / title / vote_average
     * @param sortType   排序方向：ASC / DESC
     */
    public static String mediaItemPayload(String ancestorGuid, String category, int pageSize, int page,
                                          String sortColumn, String sortType) throws JSONException {
        JSONObject body = new JSONObject();
        if (ancestorGuid != null && ancestorGuid.length() > 0) {
            body.put("ancestor_guid", ancestorGuid);
        }
        JSONObject tags = new JSONObject();
        tags.put("type", typeArray(category));
        body.put("tags", tags);
        body.put("sort_type", sortType == null || sortType.length() == 0 ? SORT_DESC : sortType);
        body.put("sort_column", sortColumn == null || sortColumn.length() == 0 ? SORT_CREATE_TIME : sortColumn);
        if (shouldExcludeGrouped(category)) {
            body.put("exclude_grouped_video", 1);
        }
        body.put("page", Math.max(1, page));
        body.put("page_size", Math.max(1, pageSize));
        return body.toString();
    }

    /** 最简请求体：只有 ancestor_guid / 排序 / 分页，不带任何筛选标签。 */
    public static String mediaItemPayloadLoose(String ancestorGuid, int pageSize, int page,
                                               String sortColumn, String sortType) throws JSONException {
        JSONObject body = new JSONObject();
        if (ancestorGuid != null && ancestorGuid.length() > 0) {
            body.put("ancestor_guid", ancestorGuid);
        }
        body.put("sort_type", sortType == null || sortType.length() == 0 ? SORT_DESC : sortType);
        body.put("sort_column", sortColumn == null || sortColumn.length() == 0 ? SORT_CREATE_TIME : sortColumn);
        body.put("page", Math.max(1, page));
        body.put("page_size", Math.max(1, pageSize));
        return body.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    /**
     * 服务端诊断：把关键接口的原始返回拼成文本。
     * 用于排查「NAS 媒体库有数据、App 里是空的」这类服务端版本/权限差异问题。
     */
    public String diagnoseMedia() {
        StringBuilder out = new StringBuilder();
        out.append("server: ").append(profile.baseUrl).append('\n');
        String detected = "";
        try {
            detected = apiBase();
        } catch (Exception ignored) {
        }
        out.append("api: ").append(detected.length() > 0 ? detected : API_V1).append("\n");
        try {
            out.append("\n== /mediadb/list ==\n")
                    .append(truncate(get("/mediadb/list").toString(), 1200)).append('\n');
        } catch (Exception ex) {
            out.append("mediadb ERR: ").append(ex.getMessage()).append('\n');
        }
        try {
            out.append("\n== /mediadb/sum ==\n")
                    .append(truncate(get("/mediadb/sum").toString(), 800)).append('\n');
        } catch (Exception ex) {
            out.append("sum ERR: ").append(ex.getMessage()).append('\n');
        }
        try {
            FnosFileList libraries = mediaLibraries();
            out.append("\nlibraries: ").append(libraries.entries.size()).append('\n');
            if (!libraries.entries.isEmpty()) {
                FnosFileEntry first = libraries.entries.get(0);
                out.append("first: ").append(first.name).append(' ').append(first.path).append('\n');
                String payload = mediaItemPayload(first.path, "all", 10);
                out.append("payload: ").append(payload).append('\n');
                out.append("resp: ").append(truncate(post("/item/list", payload).toString(), 1500)).append('\n');
            }
        } catch (Exception ex) {
            out.append("item/list ERR: ").append(ex.getMessage()).append('\n');
        }
        // 探测影视接口到底在哪个前缀下（有的版本/没装影视应用会整段 501）
        out.append("\n== 接口探测 (HTTP 状态) ==\n");
        String[][] probes = new String[][]{
                {"/v/api/v1/mediadb/list", "v1 媒体库"},
                {"/v/api/v2/mediadb/list", "v2 媒体库"},
                {"/v/api/v1/user/info", "v1 用户"},
                {"/v/api/v2/user/info", "v2 用户"},
                {"/api/v1/mediadb/list", "无 /v 前缀"},
                {"/v/", "影视 Web 应用"},
                {"/", "NAS 首页"}
        };
        for (int i = 0; i < probes.length; i++) {
            out.append(probes[i][1]).append(" ").append(probes[i][0]).append(" → ")
                    .append(probe(probes[i][0])).append('\n');
        }
        return out.toString();
    }

    /** 直接 GET 一个路径，只返回「状态码 + 前 120 字」，用于探测接口前缀。 */
    private String probe(String path) {
        Response response = null;
        try {
            String url = profile.baseUrl + path;
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", token == null ? "" : token)
                    .header("Accept", "*/*")
                    .header("X-Trim-Client", CLIENT_NAME)
                    .header("X-Trim-Client-Version", CLIENT_VERSION)
                    .build();
            response = httpClient.newCall(request).execute();
            String body = response.body() == null ? "" : response.body().string();
            return response.code() + " " + truncate(body.replace('\n', ' '), 120);
        } catch (Exception ex) {
            return "ERR " + ex.getMessage();
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /** data.total（分页总量）；缺失时返回 -1。 */
    private static int totalOf(JSONObject response) {
        JSONObject data = response == null ? null : response.optJSONObject("data");
        return data == null ? -1 : data.optInt("total", -1);
    }

    public static String mediaDetailPath(String guid) {
        String value = guid == null ? "" : guid.trim();
        if (value.length() == 0) {
            return "";
        }
        return "/item/detail?guid=" + encodeQueryValue(value);
    }

    public static FnosMediaCounts parseMediaCounts(JSONObject response) throws JSONException {
        JSONObject data = requireDataObject(response);
        int libraryCount = 0;
        Map<String, Integer> libraryCounts = new LinkedHashMap<String, Integer>();
        Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!isSummaryKey(key)) {
                libraryCount++;
                libraryCounts.put(key, data.optInt(key, 0));
            }
        }
        return new FnosMediaCounts(
                data.optInt("favorite", 0),
                libraryCount,
                data.optInt("total", 0),
                data.optInt("movie", 0),
                data.optInt("tv", 0),
                data.optInt("video", 0),
                data.optInt("live", 0),
                libraryCounts);
    }

    public static FnosFileList parseMediaItems(String path, JSONObject response) throws JSONException {
        Object data = response == null ? null : response.opt("data");
        JSONArray list = null;
        if (data instanceof JSONArray) {
            // 个别 fnOS 版本 data 直接是数组
            list = (JSONArray) data;
        } else if (data instanceof JSONObject) {
            list = ((JSONObject) data).optJSONArray("list");
        }
        return new FnosFileList(path, parseEntries(list));
    }

    public static FnosFileList parseMediaLibraries(JSONObject response) throws JSONException {
        JSONArray data = response.optJSONArray("data");
        if (data == null) {
            JSONObject object = response.optJSONObject("data");
            data = object == null ? null : object.optJSONArray("list");
        }
        List<FnosFileEntry> entries = new ArrayList<FnosFileEntry>();
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String title = firstNonEmpty(item.optString("title"), item.optString("name"), item.optString("guid"));
                String guid = firstNonEmpty(item.optString("guid"), item.optString("id"));
                if (guid.length() > 0) {
                    // 海报：网页媒体库卡用 posters 拼贴，取第一张作卡面
                    String poster = item.optString("poster");
                    if (poster.length() == 0) {
                        JSONArray posters = item.optJSONArray("posters");
                        poster = posters != null && posters.length() > 0 ? posters.optString(0) : "";
                    }
                    // category: Movie / TV / Others（网页侧栏按它换图标）
                    String category = item.optString("category");
                    entries.add(new FnosFileEntry(title, guid, true, 0L,
                            category.length() > 0 ? category : "MediaDB", "", poster));
                }
            }
        }
        if (entries.isEmpty()) {
            Logger.w("mediadb/list parsed 0 libraries, resp=" + truncate(response == null ? "" : response.toString(), 600));
        }
        return new FnosFileList("mediaLibraries", entries);
    }

    public static FnosFileList parsePlayList(JSONObject response) throws JSONException {
        Object data = response.opt("data");
        JSONArray list = null;
        if (data instanceof JSONArray) {
            list = (JSONArray) data;
        } else if (data instanceof JSONObject) {
            list = ((JSONObject) data).optJSONArray("list");
        }
        return new FnosFileList("recent", parseEntries(list));
    }

    private JSONObject get(String path) throws FnosRpcException {
        ensureToken();
        String signatureBody = signatureBody(queryOf(path));
        Request request = new Request.Builder()
                .url(apiUrl(apiBase(), path))
                .header("Authorization", token)
                .header("Accept", "application/json")
                .header("X-Trim-Client", CLIENT_NAME)
                .header("X-Trim-Client-Version", CLIENT_VERSION)
                .header("authx", authx(apiBase() + pathnameOf(path), signatureBody))
                .build();
        return execute(request);
    }

    private JSONObject post(String path, String body) throws FnosRpcException {
        ensureToken();
        String payload = body == null ? "" : body;
        Request request = new Request.Builder()
                .url(apiUrl(apiBase(), path))
                .header("Authorization", token)
                .header("Accept", "application/json")
                .header("X-Trim-Client", CLIENT_NAME)
                .header("X-Trim-Client-Version", CLIENT_VERSION)
                .header("authx", authx(apiBase() + pathnameOf(path), payload))
                .post(RequestBody.create(JSON, payload))
                .build();
        return execute(request);
    }

    /**
     * 用网页同款 v2 接口登录一次（sha256 密码）。
     * 部分 fnOS 版本会拒绝 RPC 登录（例如 131072），但 REST 是通的，此时仍可使用媒体库/播放。
     */
    public boolean tryRestLogin() {
        try {
            ensureToken();
            return token != null && token.length() > 0;
        } catch (FnosRpcException ex) {
            Logger.w("REST login failed: " + ex.getMessage());
            return false;
        } catch (RuntimeException ex) {
            Logger.w("REST login crashed: " + ex.getMessage());
            return false;
        }
    }

    private synchronized void ensureToken() throws FnosRpcException {
        if (token != null && token.length() > 0) {
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("username", profile.username);
            // v2 登录接口要求 sha256(明文密码)
            body.put("password", FnosCrypto.sha256Hex(profile.password));
            body.put("app_name", APP_NAME);
            String payload = body.toString();
            Request request = new Request.Builder()
                    .url(apiUrl(API_V2, LOGIN_PATH))
                    .header("Authorization", "")
                    .header("Accept", "application/json")
                    .header("X-Trim-Client", CLIENT_NAME)
                    .header("X-Trim-Client-Version", CLIENT_VERSION)
                    .header("authx", authx(API_V2 + LOGIN_PATH, payload))
                    .post(RequestBody.create(JSON, payload))
                    .build();
            JSONObject response = execute(request);
            token = extractToken(response);
            if (token.length() == 0) {
                throw new FnosRpcException("影视 REST 登录响应缺少 token");
            }
        } catch (JSONException ex) {
            throw new FnosRpcException("构造影视 REST 登录请求失败", ex);
        }
    }

    /**
     * 探测媒体 API 前缀：不同 fnOS 版本只有 v1 或 v2 其中一个可用，
     * 用另一个会返回 HTTP 501「Not Implemented, please update」。
     */
    private synchronized String apiBase() throws FnosRpcException {
        if (apiBaseDetected) {
            return apiBase;
        }
        ensureToken();
        apiBaseDetected = true;
        if (probeStatus(API_V1 + "/user/info") == 200) {
            apiBase = API_V1;
        } else if (probeStatus(API_V2 + "/user/info") == 200) {
            apiBase = API_V2;
            Logger.w("media API uses v2 on this server");
        } else {
            apiBase = API_V1;
        }
        return apiBase;
    }

    private int probeStatus(String path) {
        Response response = null;
        try {
            Request request = new Request.Builder()
                    .url(profile.baseUrl + path)
                    .header("Authorization", token == null ? "" : token)
                    .header("Accept", "application/json")
                    .header("X-Trim-Client", CLIENT_NAME)
                    .header("X-Trim-Client-Version", CLIENT_VERSION)
                    .build();
            response = httpClient.newCall(request).execute();
            return response.code();
        } catch (Exception ex) {
            return -1;
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private static String pathnameOf(String path) {
        if (path == null) {
            return "";
        }
        int index = path.indexOf('?');
        return index < 0 ? path : path.substring(0, index);
    }

    private static String queryOf(String path) {
        if (path == null) {
            return "";
        }
        int index = path.indexOf('?');
        return index < 0 ? "" : path.substring(index + 1);
    }

    private static String authx(String pathname, String body) throws FnosRpcException {
        String nonce = String.valueOf(100000 + NONCE_RANDOM.nextInt(900000));
        String timestamp = String.valueOf(System.currentTimeMillis());
        String bodyHash = FnosCrypto.md5Hex(body == null ? "" : body);
        String raw = SIGN_SALT + "_" + pathname + "_" + nonce + "_" + timestamp + "_" + bodyHash + "_" + SIGN_API_KEY;
        return "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + FnosCrypto.md5Hex(raw);
    }

    /** GET 请求的签名体：查询参数按 key 排序后再做 decodeURIComponent，与官方 web 客户端一致。 */
    private static String signatureBody(String query) {
        if (query == null || query.length() == 0) {
            return "";
        }
        String[] parts = query.split("&");
        Arrays.sort(parts);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == null || parts[i].length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(parts[i]);
        }
        String raw = builder.toString();
        try {
            return URLDecoder.decode(raw, "UTF-8");
        } catch (Exception ex) {
            return raw;
        }
    }


    private JSONObject execute(Request request) throws FnosRpcException {
        Response response = null;
        try {
            response = httpClient.newCall(request).execute();
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new FnosRpcException("影视 REST HTTP " + response.code());
            }
            JSONObject object = new JSONObject(body);
            int code = object.optInt("code", 0);
            if (code != 0) {
                String message = object.optString("msg");
                if (message.length() == 0) {
                    message = object.optString("message");
                }
                throw new FnosRpcException("影视 REST code=" + code + (message.length() == 0 ? "" : " " + message));
            }
            return object;
        } catch (IOException ex) {
            throw new FnosRpcException("影视 REST 请求失败", ex);
        } catch (JSONException ex) {
            throw new FnosRpcException("影视 REST 响应解析失败", ex);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private String apiUrl(String channel, String path) {
        String value = path == null ? "" : path;
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        return profile.baseUrl + channel + value;
    }

    private static String extractToken(JSONObject response) {
        String value = response.optString("token");
        if (value.length() > 0) {
            return value;
        }
        JSONObject data = response.optJSONObject("data");
        if (data != null) {
            value = firstNonEmpty(
                    data.optString("token"),
                    data.optString("access_token"),
                    data.optString("authorization"),
                    data.optString("auth"));
            if (value.length() > 0) {
                return value;
            }
        }
        return response.optString("data");
    }

    /** 网页端「筛选」分类对应的 item/list tags.type 数组。 */
    static JSONArray typeArray(String category) {
        JSONArray values = new JSONArray();
        String normalized = category == null ? "" : category.toLowerCase();
        if ("movies".equals(normalized) || "movie".equals(normalized)) {
            values.put("Movie");
        } else if ("tv".equals(normalized)) {
            values.put("TV");
        } else if ("live".equals(normalized) || "livechannel".equals(normalized)) {
            values.put("LiveChannel");
        } else if ("other".equals(normalized)) {
            values.put("Directory");
            values.put("Video");
        } else {
            // 网页 /list/all 同款：Movie + TV + Directory + Video + LiveChannel。
            values.put("Movie");
            values.put("TV");
            values.put("Directory");
            values.put("Video");
            values.put("LiveChannel");
        }
        return values;
    }

    private static boolean shouldExcludeGrouped(String category) {
        String normalized = category == null ? "" : category.toLowerCase();
        return normalized.length() == 0 || "all".equals(normalized) || "other".equals(normalized);
    }

    private static JSONObject requireDataObject(JSONObject response) throws JSONException {
        JSONObject data = response.optJSONObject("data");
        if (data == null) {
            throw new JSONException("missing data object");
        }
        return data;
    }

    private static JSONArray dataArray(JSONObject response) {
        JSONArray data = response == null ? null : response.optJSONArray("data");
        return data == null ? new JSONArray() : data;
    }

    private static List<FnosFileEntry> parseEntries(JSONArray list) {
        List<FnosFileEntry> entries = new ArrayList<FnosFileEntry>();
        if (list == null) {
            return entries;
        }
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item != null) {
                entries.add(parseEntry(item));
            }
        }
        return entries;
    }

    private static FnosFileEntry parseEntry(JSONObject item) {
        String type = item.optString("type");
        String guid = item.optString("guid");
        String name = firstNonEmpty(
                item.optString("title"),
                item.optString("tv_title"),
                item.optString("file_name"),
                item.optString("name"),
                item.optString("guid"));
        boolean directory = isDirectoryType(type);
        // 影视库条目（item/list、favorite/list）同时带真实文件 path 与条目 guid。
        // 官方 web 客户端全程按 guid 工作：详情刮削 GET /item/<guid>、播放 POST /play/info
        // 的 item_guid、进入剧集 mediaItems(ancestor_guid) 都必须用 guid。
        // 若把文件 path 当作条目身份，详情刮削与播放都会请求失败，因此媒体条目一律取 guid。
        String path = isMediaItemType(type) && guid.length() > 0
                ? guid
                : firstNonEmpty(
                        item.optString("path"),
                        item.optString("file_path"),
                        item.optString("location"),
                        item.optString("url"));
        if (path.length() == 0) {
            path = firstNonEmpty(item.optString("single_child_guid"), guid);
        }
        String entryType = directory ? type : mediaType(type, name);
        return new FnosFileEntry(
                name,
                path,
                directory,
                item.optLong("size", 0L),
                entryType,
                mediaUrl(item),
                posterPath(item),
                yearOf(item),
                scoreOf(item),
                watchedOf(item),
                firstResolution(item.optJSONObject("media_stream")),
                item.optLong("duration", 0L),
                item.optInt("season_number", 0),
                item.optInt("episode_number", 0),
                item.optString("ancestor_name"),
                item.optString("parent_title"),
                item.optString("overview"),
                item.optInt("local_number_of_episodes", 0),
                item.optString("single_child_guid"),
                item.optString("parent_guid"),
                item.optString("ancestor_guid"),
                item.optString("tv_title"));
    }

    /**
     * 是否已观看：服务端不同接口用 bool / 0-1 / 计数字段返回，
     * 这里统一兼容，避免“已观看”角标永远不出现。
     */
    private static boolean watchedOf(JSONObject item) {
        if (item == null) {
            return false;
        }
        if (item.optBoolean("watched", false) || item.optInt("watched", 0) > 0) {
            return true;
        }
        if (item.optBoolean("is_watched", false) || item.optInt("is_watched", 0) > 0) {
            return true;
        }
        return item.optBoolean("finished", false) || item.optInt("finished", 0) > 0;
    }

    /** media_stream.resolutions[0]：卡片上的分辨率角标（网页同款 "4k"/"1080p"…）。 */
    private static String firstResolution(JSONObject stream) {
        if (stream == null) {
            return "";
        }
        JSONArray resolutions = stream.optJSONArray("resolutions");
        if (resolutions == null || resolutions.length() == 0) {
            return "";
        }
        String value = resolutions.optString(0);
        return value == null ? "" : value.trim();
    }

    private static String yearOf(JSONObject item) {
        String value = firstNonEmpty(
                item.optString("release_date"),
                item.optString("first_air_date"),
                item.optString("air_date"));
        if (value.length() >= 4) {
            return value.substring(0, 4);
        }
        value = firstNonEmpty(item.optString("release_year"), item.optString("year"));
        if (value.length() >= 4) {
            return value.substring(0, 4);
        }
        return "";
    }

    private static String scoreOf(JSONObject item) {
        String value = firstNonEmpty(
                item.optString("vote_average"),
                item.optString("douban_score"),
                item.optString("rating"),
                item.optString("score"));
        try {
            double number = Double.parseDouble(value);
            if (number > 0) {
                return String.format(java.util.Locale.US, "%.1f", number);
            }
        } catch (NumberFormatException ignored) {
        }
        return "";
    }

    public static String posterImageUrl(String baseUrl, String posterPath, int width) {
        String path = posterPath == null ? "" : posterPath.trim();
        if (path.length() == 0) {
            return "";
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        String url = base + "/v/api/v1/sys/img/" + path;
        if (width > 0) {
            url += "?w=" + width;
        }
        return url;
    }

    private static String posterPath(JSONObject item) {
        String value = item.optString("poster");
        if (value.length() > 0) {
            return value;
        }
        JSONArray posters = item.optJSONArray("poster_list");
        if (posters != null && posters.length() > 0) {
            return posters.optString(0);
        }
        return "";
    }

    private static String mediaType(String type, String name) {
        String lowerType = type == null ? "" : type.toLowerCase();
        if (lowerType.indexOf("video") >= 0
                || lowerType.indexOf("movie") >= 0
                || lowerType.indexOf("episode") >= 0
                || looksVideoName(name)) {
            return "video/rest/" + type;
        }
        return type;
    }

    private static boolean isDirectoryType(String type) {
        String value = type == null ? "" : type.toLowerCase();
        return "directory".equals(value)
                || "tv".equals(value)
                || "season".equals(value)
                || "mediadb".equals(value);
    }

    /** 影视库条目类型（item/list、favorite/list、play/list 返回的条目，身份是 guid）。 */
    private static boolean isMediaItemType(String type) {
        String value = type == null ? "" : type.toLowerCase();
        return "movie".equals(value)
                || "tv".equals(value)
                || "video".equals(value)
                || "episode".equals(value)
                || "season".equals(value)
                // directory 也是媒体条目（网页 /other/:guid），身份必须是 guid。
                // 若退化成真实文件 path，点进去会落到真实目录树，"返回"就再也回不到网页结构。
                || "directory".equals(value)
                || "livechannel".equals(value);
    }

    private static String mediaUrl(JSONObject object) {
        String[] keys = {"playUrl", "downloadUrl", "fileUrl", "mediaUrl", "realUrl", "link", "src", "url"};
        for (int i = 0; i < keys.length; i++) {
            String value = object.optString(keys[i]);
            if (value.startsWith("http://") || value.startsWith("https://")) {
                return value;
            }
        }
        return "";
    }

    private static boolean looksVideoName(String name) {
        String value = name == null ? "" : name.toLowerCase();
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

    private static boolean isSummaryKey(String key) {
        return "favorite".equals(key)
                || "movie".equals(key)
                || "total".equals(key)
                || "tv".equals(key)
                || "video".equals(key)
                || "live".equals(key);
    }

    private static String firstNonEmpty(String first, String second) {
        return first != null && first.length() > 0 ? first : (second == null ? "" : second);
    }

    private static String firstNonEmpty(String first, String second, String third) {
        return firstNonEmpty(firstNonEmpty(first, second), third);
    }

    private static String firstNonEmpty(String first, String second, String third, String fourth) {
        return firstNonEmpty(firstNonEmpty(first, second, third), fourth);
    }

    private static String firstNonEmpty(String first, String second, String third, String fourth, String fifth) {
        return firstNonEmpty(firstNonEmpty(first, second, third, fourth), fifth);
    }

    private static String encodeQueryValue(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException ex) {
            return value;
        }
    }
}
