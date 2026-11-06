package ir.turk7filimo.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** داده‌های محلی: تنظیمات، پروفایل‌ها، تاریخچه‌ی تماشا و لیست تماشای بعدی (همه روی همین گوشی) */
final class Data {
    static class Entry {
        String key = "", title = "", url = "";
        long pos, dur, time;

        boolean done() {
            return dur > 0 && pos >= dur * 0.92;
        }
    }

    private Data() {}

    static SharedPreferences p(Context c) {
        return c.getSharedPreferences("lib", Context.MODE_PRIVATE);
    }

    // ---------- تنظیمات ----------
    static boolean flag(Context c, String k, boolean d) {
        return p(c).getBoolean("s_" + k, d);
    }

    static void setFlag(Context c, String k, boolean v) {
        p(c).edit().putBoolean("s_" + k, v).apply();
    }

    static boolean wifiOnly(Context c) {
        return flag(c, "wifi", false);
    }

    static boolean publicDefault(Context c) {
        return flag(c, "public", false);
    }

    static int night(Context c) {
        return p(c).getInt("s_night", 0);
    }

    static void setNight(Context c, int v) {
        p(c).edit().putInt("s_night", v).apply();
    }

    // ---------- پروفایل‌ها ----------
    static JSONArray profiles(Context c) {
        try {
            String s = p(c).getString("profiles", null);
            if (s != null) {
                JSONArray a = new JSONArray(s);
                if (a.length() > 0) return a;
            }
        } catch (Exception ignored) {
        }
        JSONArray a = new JSONArray();
        try {
            JSONObject o = new JSONObject();
            o.put("id", "p1");
            o.put("name", "من");
            a.put(o);
        } catch (Exception ignored) {
        }
        p(c).edit().putString("profiles", a.toString()).putString("cur", "p1").apply();
        return a;
    }

    static String curId(Context c) {
        JSONArray a = profiles(c);
        String cur = p(c).getString("cur", null);
        if (cur != null) {
            for (int i = 0; i < a.length(); i++) {
                if (cur.equals(a.optJSONObject(i).optString("id"))) return cur;
            }
        }
        return a.optJSONObject(0).optString("id", "p1");
    }

    static String curName(Context c) {
        JSONArray a = profiles(c);
        String id = curId(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o.optString("name", "من");
        }
        return "من";
    }

    static void setCur(Context c, String id) {
        p(c).edit().putString("cur", id).apply();
    }

    static void addProfile(Context c, String name) {
        JSONArray a = profiles(c);
        try {
            JSONObject o = new JSONObject();
            String id = "p" + System.currentTimeMillis();
            o.put("id", id);
            o.put("name", name);
            a.put(o);
            p(c).edit().putString("profiles", a.toString()).putString("cur", id).apply();
        } catch (Exception ignored) {
        }
    }

    static boolean deleteProfile(Context c, String id) {
        JSONArray a = profiles(c);
        if (a.length() <= 1) return false;
        JSONArray n = new JSONArray();
        String first = null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || id.equals(o.optString("id"))) continue;
            if (first == null) first = o.optString("id");
            n.put(o);
        }
        SharedPreferences.Editor e = p(c).edit();
        e.putString("profiles", n.toString()).remove("hist_" + id).remove("wl_" + id);
        if (id.equals(p(c).getString("cur", null)) && first != null) e.putString("cur", first);
        e.apply();
        return true;
    }

    // ---------- ذخیره‌ی لیست‌ها ----------
    private static List<Entry> load(Context c, String prefix) {
        List<Entry> l = new ArrayList<>();
        try {
            String s = p(c).getString(prefix + curId(c), "[]");
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Entry e = new Entry();
                e.key = o.optString("k");
                e.title = o.optString("t");
                e.url = o.optString("u");
                e.pos = o.optLong("p");
                e.dur = o.optLong("d");
                e.time = o.optLong("tm");
                l.add(e);
            }
        } catch (Exception ignored) {
        }
        return l;
    }

    private static void save(Context c, String prefix, List<Entry> l) {
        JSONArray a = new JSONArray();
        try {
            for (Entry e : l) {
                JSONObject o = new JSONObject();
                o.put("k", e.key);
                o.put("t", e.title);
                o.put("u", e.url);
                o.put("p", e.pos);
                o.put("d", e.dur);
                o.put("tm", e.time);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        p(c).edit().putString(prefix + curId(c), a.toString()).apply();
    }

    // ---------- تاریخچه ----------
    static List<Entry> history(Context c) {
        return load(c, "hist_");
    }

    static void saveProgress(Context c, String key, String title, long pos, long dur) {
        List<Entry> l = load(c, "hist_");
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).key.equals(key)) {
                l.remove(i);
                break;
            }
        }
        Entry e = new Entry();
        e.key = key;
        e.title = title;
        e.pos = pos;
        e.dur = dur;
        e.time = System.currentTimeMillis();
        l.add(0, e);
        while (l.size() > 200) l.remove(l.size() - 1);
        save(c, "hist_", l);
    }

    /** جای توقف قبلی (۰ اگر فیلم تمام شده یا هنوز دیده نشده) */
    static long position(Context c, String key) {
        for (Entry e : load(c, "hist_")) {
            if (e.key.equals(key)) return e.done() ? 0 : e.pos;
        }
        return 0;
    }

    static void removeHistory(Context c, String key) {
        List<Entry> l = load(c, "hist_");
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).key.equals(key)) {
                l.remove(i);
                break;
            }
        }
        save(c, "hist_", l);
    }

    static void clearHistory(Context c) {
        save(c, "hist_", new ArrayList<Entry>());
    }

    // ---------- لیست تماشای بعدی ----------
    static List<Entry> watch(Context c) {
        return load(c, "wl_");
    }

    static boolean inWatch(Context c, String url) {
        for (Entry e : load(c, "wl_")) {
            if (e.url.equals(url)) return true;
        }
        return false;
    }

    static void addWatch(Context c, String title, String url) {
        List<Entry> l = load(c, "wl_");
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).url.equals(url)) {
                l.remove(i);
                break;
            }
        }
        Entry e = new Entry();
        e.key = url;
        e.url = url;
        e.title = (title == null || title.trim().isEmpty()) ? url : title.trim();
        e.time = System.currentTimeMillis();
        l.add(0, e);
        save(c, "wl_", l);
    }

    static void removeWatch(Context c, String url) {
        List<Entry> l = load(c, "wl_");
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).url.equals(url)) {
                l.remove(i);
                break;
            }
        }
        save(c, "wl_", l);
    }
}
