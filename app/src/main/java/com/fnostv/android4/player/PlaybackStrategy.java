package com.fnostv.android4.player;

public final class PlaybackStrategy {
    private PlaybackStrategy() {
    }

    public static PlaybackOptions initialOptions(String url, String fileName, long size, boolean preferHardwareCodec) {
        // 服务器转码 HLS（本地 .m3u8 + 远程 1080P H.264 分片）：
        // 片源已被服务端降质，硬解即可流畅，不需要 mkv/ts 大文件那套兼容降级参数。
        if (isHlsPlaylist(url)) {
            return new PlaybackOptions(
                    PlaybackOptions.DECODER_HARDWARE,
                    PlaybackOptions.CACHE_REMOTE,
                    PlaybackOptions.PROFILE_STABLE,
                    6000,
                    2000,
                    false,
                    false,
                    0,
                    1024,
                    1500);
        }
        String format = extension(fileName);
        // 老格式（rm/avi/wmv…）在旧电视芯片上硬解基本不可用，统一软解。
        int decoder = preferHardwareCodec && !isLegacyFormat(format)
                ? PlaybackOptions.DECODER_HARDWARE
                : PlaybackOptions.DECODER_SOFTWARE;
        boolean highRisk = isHighRiskFormat(format, fileName, size);
        int cache = isRemoteUrl(url) ? PlaybackOptions.CACHE_REMOTE : PlaybackOptions.CACHE_STABLE;
        int networkMs = cache == PlaybackOptions.CACHE_REMOTE ? 6000 : 3000;
        int fileMs = cache == PlaybackOptions.CACHE_REMOTE ? 3000 : 2000;
        boolean lowLatency = false;
        if (cache == PlaybackOptions.CACHE_REMOTE && isLikelyLanUrl(url) && !highRisk) {
            networkMs = 2500;
            fileMs = 1000;
            lowLatency = true;
        } else if (cache == PlaybackOptions.CACHE_STABLE && !highRisk) {
            networkMs = 2500;
            fileMs = 1200;
            lowLatency = true;
        }
        boolean fluent = highRisk;
        if (fluent) {
            networkMs = Math.max(networkMs, cache == PlaybackOptions.CACHE_REMOTE ? 8000 : 5000);
            fileMs = Math.max(fileMs, 3000);
        }
        boolean fastLan = cache == PlaybackOptions.CACHE_REMOTE && lowLatency;
        int probeSizeKb = fluent ? 2048 : (fastLan ? 384 : (lowLatency ? 512 : 768));
        int analyzeDurationMs = fluent ? 2500 : (fastLan ? 600 : (lowLatency ? 800 : 1200));
        return new PlaybackOptions(
                decoder,
                cache,
                fluent ? PlaybackOptions.PROFILE_FLUENT : (lowLatency ? PlaybackOptions.PROFILE_LOW_LATENCY : PlaybackOptions.PROFILE_STABLE),
                networkMs,
                fileMs,
                fluent,
                fluent,
                fluent ? 4 : 0,
                probeSizeKb,
                analyzeDurationMs);
    }

    public static PlaybackOptions onFrequentBuffering(PlaybackOptions current) {
        if (current == null) {
            return PlaybackOptions.forUrl("", true).withFluentFallback();
        }
        int networkMs = Math.min(12000, Math.max(current.networkCachingMs + 3000, 8000));
        int fileMs = Math.min(6000, Math.max(current.fileCachingMs + 2000, 4000));
        return new PlaybackOptions(
                current.decoderMode,
                current.cacheMode,
                PlaybackOptions.PROFILE_FLUENT,
                networkMs,
                fileMs,
                true,
                true,
                Math.max(4, current.loopFilterSkip),
                Math.max(3072, current.probeSizeKb),
                Math.max(3000, current.analyzeDurationMs));
    }

    private static boolean isHighRiskFormat(String format, String fileName, long size) {
        String lowerName = lower(fileName);
        return format.equals("mkv")
                || format.equals("m2ts")
                || format.equals("ts")
                || format.equals("webm")
                || lowerName.indexOf("h265") >= 0
                || lowerName.indexOf("hevc") >= 0
                || lowerName.indexOf("x265") >= 0
                || lowerName.indexOf("2160p") >= 0
                || lowerName.indexOf("4k") >= 0
                || lowerName.indexOf("3840") >= 0
                || lowerName.indexOf("4096") >= 0
                || size > 2L * 1024L * 1024L * 1024L;
    }

    private static boolean isLegacyFormat(String format) {
        return format.equals("rm")
                || format.equals("rmvb")
                || format.equals("avi")
                || format.equals("wmv")
                || format.equals("asf")
                || format.equals("flv")
                || format.equals("vob");
    }

    private static String extension(String fileName) {
        String value = lower(fileName);
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

    private static boolean isRemoteUrl(String url) {
        String lower = lower(url);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("rtsp://");
    }

    private static boolean isHlsPlaylist(String url) {
        return lower(url).indexOf(".m3u8") >= 0;
    }

    private static boolean isLikelyLanUrl(String url) {
        String host = host(url);
        if (host.equals("localhost") || host.startsWith("127.")) {
            return true;
        }
        if (host.startsWith("10.") || host.startsWith("192.168.")) {
            return true;
        }
        if (!host.startsWith("172.")) {
            return false;
        }
        String rest = host.substring(4);
        int dot = rest.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        try {
            int second = Integer.parseInt(rest.substring(0, dot));
            return second >= 16 && second <= 31;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String host(String url) {
        String value = lower(url);
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            value = value.substring(scheme + 3);
        }
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        int at = value.lastIndexOf('@');
        if (at >= 0) {
            value = value.substring(at + 1);
        }
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(0, colon);
        }
        return value;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
