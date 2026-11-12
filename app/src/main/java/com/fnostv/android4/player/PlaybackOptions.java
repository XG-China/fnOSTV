package com.fnostv.android4.player;

public final class PlaybackOptions {
    public static final int DECODER_AUTO = 0;
    public static final int DECODER_HARDWARE = 1;
    public static final int DECODER_SOFTWARE = 2;

    public static final int CACHE_STABLE = 1;
    public static final int CACHE_REMOTE = 2;
    public static final int PROFILE_STABLE = 0;
    public static final int PROFILE_FLUENT = 1;
    public static final int PROFILE_LOW_LATENCY = 2;

    public final int decoderMode;
    public final int cacheMode;
    public final int profileMode;
    public final int networkCachingMs;
    public final int fileCachingMs;
    public final boolean allowFrameDrop;
    public final boolean fastDecode;
    public final int loopFilterSkip;
    public final int probeSizeKb;
    public final int analyzeDurationMs;

    PlaybackOptions(int decoderMode, int cacheMode, int profileMode, int networkCachingMs, int fileCachingMs,
            boolean allowFrameDrop, boolean fastDecode, int loopFilterSkip) {
        this(decoderMode, cacheMode, profileMode, networkCachingMs, fileCachingMs,
                allowFrameDrop, fastDecode, loopFilterSkip, 512, 1000);
    }

    PlaybackOptions(int decoderMode, int cacheMode, int profileMode, int networkCachingMs, int fileCachingMs,
            boolean allowFrameDrop, boolean fastDecode, int loopFilterSkip, int probeSizeKb, int analyzeDurationMs) {
        this.decoderMode = decoderMode;
        this.cacheMode = cacheMode;
        this.profileMode = profileMode;
        this.networkCachingMs = networkCachingMs;
        this.fileCachingMs = fileCachingMs;
        this.allowFrameDrop = allowFrameDrop;
        this.fastDecode = fastDecode;
        this.loopFilterSkip = loopFilterSkip;
        this.probeSizeKb = probeSizeKb;
        this.analyzeDurationMs = analyzeDurationMs;
    }

    public static PlaybackOptions forUrl(String url, boolean preferHardwareCodec) {
        int mode = DECODER_SOFTWARE;
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.KITKAT && preferHardwareCodec) {
            mode = DECODER_HARDWARE;
        }
        int cache = isRemoteUrl(url) ? CACHE_REMOTE : CACHE_STABLE;
        if (cache == CACHE_REMOTE) {
            return new PlaybackOptions(mode, cache, PROFILE_STABLE, 8000, 4000, true, true, 0);
        }
        return new PlaybackOptions(mode, cache, PROFILE_STABLE, 4000, 3000, true, true, 0);
    }

    public PlaybackOptions withSoftwareDecoder() {
        return new PlaybackOptions(DECODER_SOFTWARE, cacheMode, profileMode, networkCachingMs, fileCachingMs,
                allowFrameDrop, fastDecode, loopFilterSkip, probeSizeKb, analyzeDurationMs);
    }

    public PlaybackOptions withFluentFallback() {
        int network = Math.max(networkCachingMs, cacheMode == CACHE_REMOTE ? 6000 : 4000);
        int file = Math.max(fileCachingMs, 2500);
        return new PlaybackOptions(decoderMode, cacheMode, PROFILE_FLUENT, network, file, true, true, 4,
                Math.max(probeSizeKb, 1024), Math.max(analyzeDurationMs, 1500));
    }

    public boolean useHardwareDecoder() {
        return decoderMode == DECODER_HARDWARE || decoderMode == DECODER_AUTO;
    }

    public String decoderLabel() {
        if (decoderMode == DECODER_HARDWARE) {
            return "hardware";
        }
        if (decoderMode == DECODER_SOFTWARE) {
            return "software";
        }
        return "auto";
    }

    public String cacheLabel() {
        if (cacheMode == CACHE_REMOTE) {
            return "remote";
        }
        if (cacheMode == CACHE_STABLE) {
            return "stable";
        }
        return "balanced";
    }

    public String profileLabel() {
        if (profileMode == PROFILE_FLUENT) {
            return "fluent";
        }
        if (profileMode == PROFILE_LOW_LATENCY) {
            return "low-latency";
        }
        return "stable";
    }

    public String describe() {
        return "decoder=" + decoderLabel()
                + " profile=" + profileLabel()
                + " cache=" + cacheLabel()
                + " network=" + networkCachingMs
                + " file=" + fileCachingMs
                + " framedrop=" + allowFrameDrop
                + " fast=" + fastDecode
                + " skiploop=" + loopFilterSkip
                + " probeKb=" + probeSizeKb
                + " analyzeMs=" + analyzeDurationMs;
    }

    private static boolean isRemoteUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("rtsp://");
    }
}
