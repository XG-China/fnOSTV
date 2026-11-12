package com.fnostv.android4.media;

import android.content.Context;
import android.content.SharedPreferences;

import com.fnostv.android4.net.FnosFileEntry;
import com.fnostv.android4.util.Constants;
import com.fnostv.android4.util.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class MediaIndexStore {
    private static final String KEY_INDEX = "native_media_index";
    private static final int MAX_ITEMS = 1000;

    private final SharedPreferences preferences;

    public MediaIndexStore(Context context) {
        preferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<FnosFileEntry> list() {
        List<FnosFileEntry> entries = new ArrayList<FnosFileEntry>();
        String raw = preferences.getString(KEY_INDEX, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) {
                    entries.add(fromJson(object));
                }
            }
        } catch (JSONException ex) {
            Logger.w("Media index parse failed: " + ex.getMessage());
        }
        return entries;
    }

    public void clear() {
        preferences.edit().remove(KEY_INDEX).apply();
    }

    private FnosFileEntry fromJson(JSONObject object) {
        return new FnosFileEntry(
                object.optString("name"),
                object.optString("path"),
                false,
                object.optLong("size", 0L),
                object.optString("type"),
                object.optString("mediaUrl"));
    }

    private JSONObject toJson(FnosFileEntry entry) {
        JSONObject object = new JSONObject();
        try {
            object.put("name", entry.name);
            object.put("path", entry.path);
            object.put("size", entry.size);
            object.put("type", entry.type);
            object.put("mediaUrl", entry.mediaUrl);
            object.put("indexedAt", System.currentTimeMillis());
        } catch (JSONException ignored) {
        }
        return object;
    }
}
