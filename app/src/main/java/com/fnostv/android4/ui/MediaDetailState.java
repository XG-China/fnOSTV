package com.fnostv.android4.ui;

import com.fnostv.android4.net.FnosFileEntry;
import com.fnostv.android4.net.FnosPlaybackSource;
import com.fnostv.android4.net.FnosRestClient;
import com.fnostv.android4.net.MediaDetailInfo;
import com.fnostv.android4.player.PlaybackSourceSelector;

import java.util.ArrayList;
import java.util.List;

public final class MediaDetailState {
    public final FnosFileEntry entry;
    public boolean favorite;
    public boolean loadingSources;
    public boolean loadingDetail;
    public String errorMessage = "";
    public String detailError = "";
    public MediaDetailInfo detailInfo = MediaDetailInfo.empty();
    /** 详情页进度：观看位置 / 时长（秒），来自 play/info。 */
    public long positionSeconds;
    public long durationSeconds;
    public boolean hasSubtitle;
    /** 非空表示当前播放源是服务器转码 HLS，播放期间需要周期上报进度保活。 */
    public FnosRestClient.TranscodeSession transcode;
    private final List<FnosPlaybackSource> sources = new ArrayList<FnosPlaybackSource>();
    private int selectedSourceIndex;

    public MediaDetailState(FnosFileEntry entry, boolean favorite) {
        this.entry = entry;
        this.favorite = favorite;
    }

    public void setLoadingSources(boolean loadingSources) {
        this.loadingSources = loadingSources;
        if (loadingSources) {
            errorMessage = "";
        }
    }

    public void setError(String errorMessage) {
        this.loadingSources = false;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public void setLoadingDetail(boolean loadingDetail) {
        this.loadingDetail = loadingDetail;
        if (loadingDetail) {
            detailError = "";
        }
    }

    public void setDetailInfo(MediaDetailInfo detailInfo) {
        this.detailInfo = detailInfo == null ? MediaDetailInfo.empty() : detailInfo;
        loadingDetail = false;
        detailError = "";
    }

    public void setDetailError(String detailError) {
        this.loadingDetail = false;
        this.detailError = detailError == null ? "" : detailError;
    }

    public void setSources(List<FnosPlaybackSource> resolvedSources) {
        setSources(resolvedSources, null);
    }

    public void setSources(List<FnosPlaybackSource> resolvedSources, FnosRestClient.TranscodeSession transcodeSession) {
        sources.clear();
        sources.addAll(PlaybackSourceSelector.normalize(resolvedSources));
        selectedSourceIndex = PlaybackSourceSelector.clampIndex(sources, selectedSourceIndex);
        transcode = transcodeSession;
        loadingSources = false;
        errorMessage = "";
    }

    public void setProgress(long positionSeconds, long durationSeconds, boolean hasSubtitle) {
        this.positionSeconds = positionSeconds < 0 ? 0L : positionSeconds;
        this.durationSeconds = durationSeconds < 0 ? 0L : durationSeconds;
        this.hasSubtitle = hasSubtitle;
    }

    /** 观看进度比例（0..1）。 */
    public float progressRatio() {
        if (durationSeconds <= 0L || positionSeconds <= 0L) {
            return 0f;
        }
        double ratio = (double) positionSeconds / (double) durationSeconds;
        if (ratio <= 0d) {
            return 0f;
        }
        return ratio >= 1d ? 1f : (float) ratio;
    }

    /** 网页同款"剩余 X 小时 X 分钟"。 */
    public String remainingLabel() {
        long remain = durationSeconds - positionSeconds;
        if (remain <= 0 || positionSeconds <= 0) {
            return "";
        }
        long minutes = remain / 60L;
        if (minutes <= 0) {
            minutes = 1L;
        }
        long hours = minutes / 60L;
        long rest = minutes % 60L;
        if (hours > 0 && rest > 0) {
            return "剩余 " + hours + " 小时 " + rest + " 分钟";
        }
        if (hours > 0) {
            return "剩余 " + hours + " 小时";
        }
        return "剩余 " + rest + " 分钟";
    }

    public boolean hasProgress() {
        return positionSeconds > 0 && durationSeconds > positionSeconds;
    }

    public List<FnosPlaybackSource> sources() {
        return new ArrayList<FnosPlaybackSource>(sources);
    }

    public List<FnosPlaybackSource> sourcesForPlayback() {
        List<FnosPlaybackSource> ordered = sources();
        if (ordered.size() <= 1) {
            return ordered;
        }
        int selected = PlaybackSourceSelector.clampIndex(ordered, selectedSourceIndex);
        FnosPlaybackSource first = ordered.remove(selected);
        ordered.add(0, first);
        return ordered;
    }

    public void selectSource(int index) {
        selectedSourceIndex = PlaybackSourceSelector.clampIndex(sources, index);
    }

    public int selectedSourceIndex() {
        return selectedSourceIndex;
    }

    public FnosPlaybackSource currentSource() {
        return PlaybackSourceSelector.selectedSource(sources, selectedSourceIndex);
    }

    public String sourceLabel() {
        return PlaybackSourceSelector.displayLabel(sources, selectedSourceIndex);
    }
}
