package com.fnostv.android4.ui;

import com.fnostv.android4.net.FnosFileEntry;

import java.util.ArrayList;
import java.util.List;

public final class HomePosterSections {
    private static final int RECENT_LIMIT = 6;
    private static final int FAVORITE_LIMIT = 6;
    private static final int MEDIA_LIMIT = 8;

    private HomePosterSections() {
    }

    /**
     * 与网页首页一致：继续观看 + 每个媒体库一个分区（分区标题=库名，action=library:&lt;guid&gt;），
     * 最后追加非空的我的收藏分区。
     */
    public static List<HomePosterSection> forLibraries(
            List<FnosFileEntry> recent,
            List<FnosFileEntry> favorite,
            List<HomePosterSection> librarySections) {
        List<HomePosterSection> sections = new ArrayList<HomePosterSection>();
        List<FnosFileEntry> recentEntries = clean(recent);
        if (recentEntries.size() > 0) {
            sections.add(new HomePosterSection("继续观看", NativeHomeView.ACTION_RECENT, recentEntries, RECENT_LIMIT));
        }
        if (librarySections != null) {
            for (int i = 0; i < librarySections.size(); i++) {
                HomePosterSection section = librarySections.get(i);
                if (section != null && section.entries.size() > 0) {
                    sections.add(section);
                }
            }
        }
        List<FnosFileEntry> favoriteEntries = clean(favorite);
        if (favoriteEntries.size() > 0) {
            sections.add(new HomePosterSection("我的收藏", NativeHomeView.ACTION_FAVORITES, favoriteEntries, FAVORITE_LIMIT));
        }
        return sections;
    }

    /** 单个库的分区（标题=库名，action=library:&lt;guid&gt;）。 */
    public static HomePosterSection forLibrary(String title, String guid, List<FnosFileEntry> entries) {
        return new HomePosterSection(
                title,
                NativeHomeView.ACTION_LIBRARY_PREFIX + guid,
                entries,
                MEDIA_LIMIT);
    }

    private static List<FnosFileEntry> clean(List<FnosFileEntry> entries) {
        List<FnosFileEntry> values = new ArrayList<FnosFileEntry>();
        if (entries == null) {
            return values;
        }
        for (int i = 0; i < entries.size(); i++) {
            FnosFileEntry entry = entries.get(i);
            if (entry == null || containsPath(values, entry.path)) {
                continue;
            }
            if (entry.isVideo() || (entry.directory && entry.posterPath.length() > 0)) {
                values.add(entry);
            }
        }
        return values;
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
