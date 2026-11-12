package com.fnostv.android4.media;

import com.fnostv.android4.net.FnosFileEntry;
import com.fnostv.android4.net.FnosFileList;
import com.fnostv.android4.net.FnosRpcException;
import com.fnostv.android4.ui.NativeHomeView;

import java.util.ArrayList;
import java.util.List;

/**
 * MediaCenterGateway 行为用例（纯 main() 运行，不依赖 JUnit）：
 * 所有内容都来自 REST，不再回退到本地索引/文件目录树。
 */
public final class MediaCenterGatewayTest {
    public static void main(String[] args) throws Exception {
        restItemsWin();
        folderNodeUsesParentGuid();
        libraryNodeUsesAncestorGuid();
        emptyRestReturnsFailure();
        missingProviderReturnsFailure();
    }

    private static void restItemsWin() {
        FakeRest rest = new FakeRest();
        rest.items = list("", video("Movie A", "guid-a"));

        MediaCenterLoad result = new MediaCenterGateway(rest).load("");

        assertEquals(true, result.success);
        assertEquals("全部", result.title);
        assertEquals("Movie A", result.list.entries.get(0).name);
        assertEquals(MediaCenterLoad.SOURCE_REST_ITEMS, result.source);
    }

    private static void folderNodeUsesParentGuid() {
        FakeRest rest = new FakeRest();
        rest.folder = list("folder-guid", dir("Season 1", "season-guid"));

        MediaCenterLoad result = new MediaCenterGateway(rest).load(MediaCenterGateway.NODE_FOLDER + "folder-guid");

        assertEquals(true, result.success);
        assertEquals("Season 1", result.list.entries.get(0).name);
        assertEquals(MediaCenterLoad.SOURCE_REST_ITEMS, result.source);
    }

    private static void libraryNodeUsesAncestorGuid() {
        FakeRest rest = new FakeRest();
        rest.items = list("library-guid", video("Episode", "episode-guid"));

        MediaCenterLoad result = new MediaCenterGateway(rest).load("library-guid");

        assertEquals(true, result.success);
        assertEquals("Episode", result.list.entries.get(0).name);
        assertEquals(MediaCenterLoad.SOURCE_REST_ITEMS, result.source);
    }

    private static void emptyRestReturnsFailure() {
        FakeRest rest = new FakeRest();
        rest.items = list("empty");

        MediaCenterLoad result = new MediaCenterGateway(rest).load("");

        assertEquals(false, result.success);
        assertEquals(true, result.message.length() > 0);
    }

    private static void missingProviderReturnsFailure() {
        MediaCenterLoad result = new MediaCenterGateway(null).load("");

        assertEquals(false, result.success);
    }

    private static final class FakeRest implements MediaCenterGateway.RestProvider {
        FnosFileList libraries = list("libraries");
        FnosFileList items = list("items");
        FnosFileList folder = list("folder");

        @Override
        public FnosFileList libraries() {
            return libraries;
        }

        @Override
        public FnosFileList items(String path, String category, int pageSize) throws FnosRpcException {
            assertEquals(NativeHomeView.ACTION_ALL, category);
            assertEquals(50, pageSize);
            return items;
        }

        @Override
        public FnosFileList folderItems(String folderGuid, int pageSize) throws FnosRpcException {
            assertEquals(50, pageSize);
            return folder;
        }
    }

    private static FnosFileList list(String path, FnosFileEntry... entries) {
        List<FnosFileEntry> values = new ArrayList<FnosFileEntry>();
        for (int i = 0; i < entries.length; i++) {
            values.add(entries[i]);
        }
        return new FnosFileList(path, values);
    }

    private static FnosFileEntry video(String name, String guid) {
        return new FnosFileEntry(name, guid, false, 0L, "video/rest/Movie", "", "");
    }

    private static FnosFileEntry dir(String name, String guid) {
        return new FnosFileEntry(name, guid, true, 0L, "dir", "", "");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }
}
