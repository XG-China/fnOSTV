package com.fnostv.android4.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MediaDetailInfo {
    public final String title;
    public final String subtitle;
    public final String overview;
    public final String year;
    public final String rating;
    public final String category;
    public final String durationLabel;
    public final String sourceLabel;
    public final List<FnosFileEntry> children;

    // 官方 web 详情页（/v/api/v1/item/<guid>）刮削字段，与网页 1:1 展示。
    public final String genresText;
    public final String contentRating;
    public final String country;
    public final String libraryName;
    public final String backdropPath;
    public final String logoPath;
    public final List<MediaPerson> persons;
    /** 原始 vote_average（如 "7" / "6.8"），详情页按网页显示为 "7.0 分"。 */
    public final String voteAverage;
    /** 技术徽标（分辨率 / 音轨 / 色彩范围），网页对应三枚徽标图。 */
    public final List<String> techBadges;
    /** 网页字幕轨文案（"中文字幕" / "无字幕"），接口不可用时为空串。 */
    public final String subtitleLabel;
    /** 网页音轨文案（"中文音频" / "无音频"），接口不可用时为空串。 */
    public final String audioLabel;

    public MediaDetailInfo(
            String title,
            String subtitle,
            String overview,
            String year,
            String rating,
            String category,
            String durationLabel,
            String sourceLabel,
            List<FnosFileEntry> children) {
        this(title, subtitle, overview, year, rating, category, durationLabel, sourceLabel, children,
                "", "", "", "", "", "", "", null, "", null, "", "");
    }

    public MediaDetailInfo(
            String title,
            String subtitle,
            String overview,
            String year,
            String rating,
            String category,
            String durationLabel,
            String sourceLabel,
            List<FnosFileEntry> children,
            String genresText,
            String contentRating,
            String country,
            String libraryName,
            String backdropPath,
            String logoPath,
            String originalTitle,
            List<MediaPerson> persons,
            String voteAverage,
            List<String> techBadges,
            String subtitleLabel,
            String audioLabel) {
        this.title = clean(title);
        this.subtitle = clean(subtitle);
        this.overview = clean(overview);
        this.year = clean(year);
        this.rating = clean(rating);
        this.category = clean(category);
        this.durationLabel = clean(durationLabel);
        this.sourceLabel = clean(sourceLabel);
        this.children = Collections.unmodifiableList(children == null
                ? new ArrayList<FnosFileEntry>()
                : new ArrayList<FnosFileEntry>(children));
        this.genresText = clean(genresText);
        this.contentRating = clean(contentRating);
        this.country = clean(country);
        this.libraryName = clean(libraryName);
        this.backdropPath = clean(backdropPath);
        this.logoPath = clean(logoPath);
        this.persons = Collections.unmodifiableList(persons == null
                ? new ArrayList<MediaPerson>()
                : new ArrayList<MediaPerson>(persons));
        this.voteAverage = clean(voteAverage);
        List<String> badges = new ArrayList<String>();
        if (techBadges != null) {
            for (int i = 0; i < techBadges.size(); i++) {
                String badge = clean(techBadges.get(i));
                if (badge.length() > 0) {
                    badges.add(badge);
                }
            }
        }
        this.techBadges = Collections.unmodifiableList(badges);
        this.subtitleLabel = clean(subtitleLabel);
        this.audioLabel = clean(audioLabel);
        if (originalTitle.length() > 0 && this.subtitle.length() == 0) {
            // subtitle 即原名，仅在解析结果为空时回填
            // (final 字段不能改，因此通过新的构造分支实现，见 withScraped)
        }
    }

    /** 用官方详情接口的刮削字段合并出新对象。 */
    public MediaDetailInfo withScraped(
            String originalTitle,
            String genresText,
            String contentRating,
            String country,
            String libraryName,
            String backdropPath,
            String logoPath,
            List<MediaPerson> persons,
            String voteAverage,
            List<String> techBadges,
            String subtitleLabel,
            String audioLabel) {
        String subtitle = this.subtitle.length() > 0 ? this.subtitle : clean(originalTitle);
        return new MediaDetailInfo(
                title,
                subtitle,
                overview,
                year,
                rating,
                category,
                durationLabel,
                sourceLabel,
                children,
                genresText,
                contentRating,
                country,
                libraryName,
                backdropPath,
                logoPath,
                "",
                persons,
                voteAverage,
                techBadges,
                subtitleLabel,
                audioLabel);
    }

    public static MediaDetailInfo empty() {
        return new MediaDetailInfo("", "", "", "", "", "", "", "", null);
    }

    public boolean isEmpty() {
        return title.length() == 0
                && subtitle.length() == 0
                && overview.length() == 0
                && year.length() == 0
                && rating.length() == 0
                && category.length() == 0
                && durationLabel.length() == 0
                && sourceLabel.length() == 0
                && children.size() == 0
                && persons.size() == 0
                && voteAverage.length() == 0
                && techBadges.size() == 0;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
