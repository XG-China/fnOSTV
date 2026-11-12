package com.fnostv.android4.net;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FnosMediaCounts {
    public final int favoriteCount;
    public final int libraryCount;
    public final int totalCount;
    public final int movieCount;
    public final int tvCount;
    public final int otherCount;
    /** mediadb/sum 里的电视直播数量（网页侧栏"电视直播"）。 */
    public final int liveCount;
    /** 每个媒体库 guid → 条目数（网页侧栏/首页媒体库卡计数）。 */
    public final Map<String, Integer> libraryCounts;

    public FnosMediaCounts(int favoriteCount, int libraryCount, int totalCount, int movieCount, int tvCount, int otherCount) {
        this(favoriteCount, libraryCount, totalCount, movieCount, tvCount, otherCount, 0, null);
    }

    public FnosMediaCounts(
            int favoriteCount,
            int libraryCount,
            int totalCount,
            int movieCount,
            int tvCount,
            int otherCount,
            int liveCount,
            Map<String, Integer> libraryCounts) {
        this.favoriteCount = Math.max(0, favoriteCount);
        this.libraryCount = Math.max(0, libraryCount);
        this.totalCount = Math.max(0, totalCount);
        this.movieCount = Math.max(0, movieCount);
        this.tvCount = Math.max(0, tvCount);
        this.otherCount = Math.max(0, otherCount);
        this.liveCount = Math.max(0, liveCount);
        this.libraryCounts = libraryCounts == null
                ? Collections.<String, Integer>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(libraryCounts));
    }

    public int countForLibrary(String guid) {
        Integer value = libraryCounts.get(guid);
        return value == null ? 0 : value;
    }
}
