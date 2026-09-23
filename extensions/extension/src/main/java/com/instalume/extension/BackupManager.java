package com.instalume.extension;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * JSON backup/restore — schemaVersion 1.
 * Never includes passwords, tokens, cookies, or credentials.
 * Unknown future keys are preserved on merge-without-overwrite.
 */
public final class BackupManager {
    public static final int SCHEMA = 1;
    private static final Set<String> DENY = new HashSet<>();
    static {
        String[] d = {"password","passwd","token","auth","cookie","session","secret","credential","mqtt"};
        for (String s : d) DENY.add(s);
    }

    private BackupManager() {}

    public static boolean isSensitive(String k) {
        if (k == null) return true;
        String l = k.toLowerCase();
        for (String s : DENY) if (l.contains(s)) return true;
        return false;
    }

    public static JSONObject exportJson(Context ctx) throws Exception {
        Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        SharedPreferences p = app.getSharedPreferences("instalume_prefs", Context.MODE_PRIVATE);
        JSONObject root = new JSONObject();
        root.put("schemaVersion", SCHEMA);
        root.put("appVersion", BuildInfo.VERSION);
        root.put("product", BuildInfo.PRODUCT);
        JSONObject settings = new JSONObject();
        JSONObject theme = new JSONObject();
        JSONObject nav = new JSONObject();
        JSONObject privacy = new JSONObject();
        JSONObject features = new JSONObject();
        if (p != null) {
            for (java.util.Map.Entry<String, ?> e : p.getAll().entrySet()) {
                String k = e.getKey();
                if (isSensitive(k)) continue;
                Object v = e.getValue();
                JSONObject bucket = settings;
                if (k.startsWith("theme_") || k.startsWith("glass_") || k.startsWith("blur_")
                        || k.startsWith("corner_") || k.startsWith("icon_")) bucket = theme;
                else if (k.startsWith("nav_")) bucket = nav;
                else if (k.startsWith("block_") || k.startsWith("ghost_") || k.startsWith("limit_")) bucket = privacy;
                else if (k.startsWith("debug_") || k.startsWith("auto_") || k.startsWith("url_")
                        || k.startsWith("compact_") || k.startsWith("reduced_") || k.startsWith("distraction_")) bucket = features;
                if (v instanceof Boolean) bucket.put(k, (Boolean) v);
                else if (v instanceof Integer) bucket.put(k, (Integer) v);
                else if (v instanceof Long) bucket.put(k, (Long) v);
                else if (v instanceof Float) bucket.put(k, ((Float) v).doubleValue());
                else if (v != null) bucket.put(k, v.toString());
            }
        }
        root.put("settings", settings);
        root.put("theme", theme);
        root.put("navigation", nav);
        root.put("privacy", privacy);
        root.put("features", features);
        return root;
    }

    public static void importJson(Context ctx, String json, boolean overwrite) throws Exception {
        if (json == null || json.length() > 512 * 1024) throw new IllegalArgumentException("backup too large or empty");
        JSONObject root = new JSONObject(json);
        int schema = root.optInt("schemaVersion", -1);
        if (schema != 1) throw new IllegalArgumentException("unsupported schemaVersion=" + schema);
        Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        SharedPreferences p = app.getSharedPreferences("instalume_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = p.edit();
        String[] buckets = {"settings","theme","navigation","privacy","features"};
        for (String b : buckets) {
            JSONObject o = root.optJSONObject(b);
            if (o == null) continue;
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (isSensitive(k)) continue;
                if (!overwrite && p.contains(k)) continue; // preserve existing + unknown future keys
                Object v = o.get(k);
                if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
                else if (v instanceof Integer) ed.putInt(k, (Integer) v);
                else if (v instanceof Long) ed.putLong(k, (Long) v);
                else if (v instanceof Double) ed.putFloat(k, ((Double) v).floatValue());
                else ed.putString(k, String.valueOf(v));
            }
        }
        ed.apply();
        Config.setNeedsRestart();
    }

    public static void clearDeveloper(Context ctx) {
        Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        SharedPreferences p = app.getSharedPreferences("instalume_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = p.edit();
        for (String k : p.getAll().keySet()) {
            if (k.startsWith("debug_")) ed.remove(k);
        }
        ed.apply();
    }
}
