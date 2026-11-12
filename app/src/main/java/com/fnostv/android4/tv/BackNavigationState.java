package com.fnostv.android4.tv;

/** 返回键优先级：播放器 → 详情 → 列表 → 首页 → 交还系统。 */
public enum BackNavigationState {
    PLAYER,
    DETAIL,
    BROWSER,
    SYSTEM;

    public static BackNavigationState choose(
            boolean playerVisible,
            boolean detailVisible,
            boolean browserVisible,
            boolean homeVisible) {
        if (playerVisible) {
            return PLAYER;
        }
        if (detailVisible) {
            return DETAIL;
        }
        if (browserVisible) {
            return BROWSER;
        }
        return SYSTEM;
    }
}
