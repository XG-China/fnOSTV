package com.fnostv.android4.media;

import com.fnostv.android4.net.FnosFileEntry;
import com.fnostv.android4.net.FnosFileList;
import com.fnostv.android4.net.FnosRpcException;
import com.fnostv.android4.ui.NativeHomeView;

import java.util.ArrayList;
import java.util.List;

/**
 * 影视中心网关。
 *
 * <p>严格对齐网页端「飞牛影视」的取数方式：所有内容都来自
 * {@code GET /mediadb/list} + {@code POST /item/list}，条目身份一律是 guid。
 *
 * <p><b>刻意不再回退到真实文件系统目录树。</b>网页端不存在「真实目录」这种页面，
 * 之前的回退导致 TV 端点进媒体条目后掉进 /volX/... 目录树，
 * 且「返回」永远退不出该结构（用户反馈：返回会一直显示真实目录）。
 */
public final class MediaCenterGateway {
    /**
     * 文件夹节点前缀（网页 {@code /folder/:guid}）。
     *
     * <p>文件夹的子条目必须用 {@code parent_guid} 取，媒体库必须用 {@code ancestor_guid} 取，
     * 两者互不通用（用错会拿到 0 条），所以节点路径要带上类型前缀。
     */
    public static final String NODE_FOLDER = "dir:";

    public interface RestProvider {
        FnosFileList libraries() throws FnosRpcException;

        FnosFileList items(String path, String category, int pageSize) throws FnosRpcException;

        /** 文件夹直接子条目（网页 /folder/:guid 同款：parent_guid）。 */
        FnosFileList folderItems(String folderGuid, int pageSize) throws FnosRpcException;
    }

    /** 单页条数：网页端一页 50，TV 端一次拉全（内部已分页循环）。 */
    private static final int PAGE_SIZE = 50;

    private final RestProvider restProvider;

    public MediaCenterGateway(RestProvider restProvider) {
        this.restProvider = restProvider;
    }

    /**
     * 载入一个影视节点。
     *
     * @param path 空串 = 网页 {@code /list/all}（全部条目）；
     *             {@code dir:<guid>} = 网页 {@code /folder/:guid}（用 parent_guid 取直接子条目）；
     *             其他非空 = 网页 {@code /library/:guid}（用 ancestor_guid 取子条目）。
     */
    public MediaCenterLoad load(String path) {
        String value = path == null ? "" : path;
        List<String> trace = new ArrayList<String>();
        if (restProvider == null) {
            trace.add("REST: provider missing");
            return MediaCenterLoad.failure(traceMessage(trace));
        }
        try {
            if (value.length() == 0) {
                // 网页 /list/all：跨全部媒体库的条目列表。
                FnosFileList all = restProvider.items("", NativeHomeView.ACTION_ALL, PAGE_SIZE);
                if (hasEntries(all)) {
                    return MediaCenterLoad.success(
                            "全部",
                            "",
                            all,
                            false,
                            MediaCenterLoad.SOURCE_REST_ITEMS);
                }
                trace.add("REST: empty");
                return MediaCenterLoad.failure(traceMessage(trace));
            }
            if (value.startsWith(NODE_FOLDER)) {
                // 网页 /folder/:guid：文件夹必须用 parent_guid 取直接子级，
                // 且不能带 exclude_grouped_video（文件夹里的视频正是成组视频）。
                String guid = value.substring(NODE_FOLDER.length());
                FnosFileList child = restProvider.folderItems(guid, PAGE_SIZE);
                return MediaCenterLoad.success(
                        folderTitle(child),
                        folderSubtitle(child),
                        child == null ? new FnosFileList(value, new ArrayList<FnosFileEntry>()) : child,
                        false,
                        MediaCenterLoad.SOURCE_REST_ITEMS);
            }
            // 网页 /library/:guid：按 ancestor_guid 取子条目。
            FnosFileList child = restProvider.items(value, NativeHomeView.ACTION_ALL, PAGE_SIZE);
            String title = libraryTitle(child);
            if (hasEntries(child)) {
                return MediaCenterLoad.success(
                        title,
                        "",
                        child,
                        false,
                        MediaCenterLoad.SOURCE_REST_ITEMS);
            }
            // 合法但没有子条目：返回成功 + 空列表，由 UI 显示「无数据」。
            return MediaCenterLoad.success(
                    title,
                    "暂无内容",
                    child == null ? new FnosFileList(value, new ArrayList<FnosFileEntry>()) : child,
                    false,
                    MediaCenterLoad.SOURCE_REST_ITEMS);
        } catch (FnosRpcException ex) {
            trace.add("REST: " + ex.getMessage());
        } catch (RuntimeException ex) {
            trace.add("REST: " + ex.getMessage());
        }
        return MediaCenterLoad.failure(traceMessage(trace));
    }

    /**
     * 文件夹页主标题：网页端用文件夹名（子条目的 parent_title）。
     */
    private static String folderTitle(FnosFileList list) {
        if (!hasEntries(list)) {
            return "文件夹";
        }
        FnosFileEntry first = list.entries.get(0);
        if (first == null) {
            return "文件夹";
        }
        if (first.parentTitle != null && first.parentTitle.length() > 0) {
            return first.parentTitle;
        }
        return first.libraryName != null && first.libraryName.length() > 0 ? first.libraryName : "文件夹";
    }

    /**
     * 文件夹页副标题：网页端显示所属媒体库（ancestor_name）。
     */
    private static String folderSubtitle(FnosFileList list) {
        if (!hasEntries(list)) {
            return "";
        }
        FnosFileEntry first = list.entries.get(0);
        if (first == null || first.libraryName == null) {
            return "";
        }
        return first.libraryName;
    }

    /**
     * 媒体库页标题：子条目的 ancestor_name 即所属媒体库名（网页 /library/:guid 同款）。
     */
    private static String libraryTitle(FnosFileList list) {
        if (!hasEntries(list)) {
            return "媒体库";
        }
        FnosFileEntry first = list.entries.get(0);
        if (first == null || first.libraryName == null || first.libraryName.length() == 0) {
            return "媒体库";
        }
        return first.libraryName;
    }

    /** 媒体库列表（网页侧栏「媒体库」区块）。 */
    public FnosFileList libraries() throws FnosRpcException {
        if (restProvider == null) {
            throw new FnosRpcException("REST provider missing");
        }
        return restProvider.libraries();
    }

    private static boolean hasEntries(FnosFileList list) {
        return list != null && list.entries != null && list.entries.size() > 0;
    }

    private static String traceMessage(List<String> trace) {
        if (trace == null || trace.size() == 0) {
            return "无数据";
        }
        StringBuilder builder = new StringBuilder("无数据：");
        for (int i = 0; i < trace.size(); i++) {
            if (i > 0) {
                builder.append("；");
            }
            builder.append(trace.get(i));
        }
        return builder.toString();
    }
}
