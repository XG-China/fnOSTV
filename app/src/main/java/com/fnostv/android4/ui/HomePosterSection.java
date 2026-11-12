package com.fnostv.android4.ui;

import com.fnostv.android4.net.FnosFileEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HomePosterSection {
    public final String title;
    public final String action;
    public final List<FnosFileEntry> entries;
    public final int maxVisible;
    public final boolean hasMore;

    public HomePosterSection(String title, String action, List<FnosFileEntry> entries, int maxVisible) {
        this.title = title == null ? "" : title;
        this.action = action == null ? "" : action;
        this.entries = Collections.unmodifiableList(entries == null
                ? new ArrayList<FnosFileEntry>()
                : new ArrayList<FnosFileEntry>(entries));
        this.maxVisible = Math.max(1, maxVisible);
        this.hasMore = this.entries.size() > this.maxVisible;
    }

    public List<FnosFileEntry> visibleEntries() {
        int end = Math.min(entries.size(), maxVisible);
        return Collections.unmodifiableList(new ArrayList<FnosFileEntry>(entries.subList(0, end)));
    }
}
