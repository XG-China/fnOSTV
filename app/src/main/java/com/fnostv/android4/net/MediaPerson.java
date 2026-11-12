package com.fnostv.android4.net;

/** 演职人员（网页详情页"演职人员"横滚卡片）。 */
public final class MediaPerson {
    public final String name;
    public final String roleLabel;
    public final String profilePath;
    public final int order;
    /** 演职人员 guid（网页端 /person/:guid 页面用）。 */
    public final String personGuid;

    public MediaPerson(String name, String roleLabel, String profilePath, int order) {
        this(name, roleLabel, profilePath, order, "");
    }

    public MediaPerson(String name, String roleLabel, String profilePath, int order, String personGuid) {
        this.name = name == null ? "" : name.trim();
        this.roleLabel = roleLabel == null ? "" : roleLabel.trim();
        this.profilePath = profilePath == null ? "" : profilePath.trim();
        this.order = order;
        this.personGuid = personGuid == null ? "" : personGuid.trim();
    }
}
