package ir.turk7filimo.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/** انتقال فیلم‌های دانلودشده بین «خصوصی» (فقط داخل اپ) و «گالری» (پوشه‌ی Movies/turk7filimo) */
final class Publisher {
    private Publisher() {}

    static boolean supported() {
        return Build.VERSION.SDK_INT >= 29;
    }

    static String mime(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".mkv")) return "video/x-matroska";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".avi")) return "video/x-msvideo";
        if (n.endsWith(".mov")) return "video/quicktime";
        if (n.endsWith(".3gp")) return "video/3gpp";
        return "video/mp4";
    }

    // ---------- فهرست فایل‌های داخل گالری ----------
    static JSONObject records(Context c) {
        try {
            return new JSONObject(Store.prefs(c).getString("pubrec", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static void saveRecords(Context c, JSONObject o) {
        Store.prefs(c).edit().putString("pubrec", o.toString()).apply();
    }

    static void putRec(Context c, String name, Uri u, long size) {
        try {
            JSONObject all = records(c);
            JSONObject r = new JSONObject();
            r.put("uri", u.toString());
            r.put("size", size);
            r.put("time", System.currentTimeMillis());
            all.put(name, r);
            saveRecords(c, all);
        } catch (Exception ignored) {
        }
    }

    static void removeRec(Context c, String name) {
        JSONObject all = records(c);
        all.remove(name);
        saveRecords(c, all);
    }

    static boolean isPublic(Context c, String name) {
        return records(c).has(name);
    }

    static String uriOf(Context c, String name) {
        JSONObject r = records(c).optJSONObject(name);
        return r == null ? null : r.optString("uri", null);
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[1 << 16];
        int r;
        while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        out.flush();
    }

    // ---------- خصوصی ← گالری ----------
    static Uri publish(Context c, File f, String name) {
        if (!supported()) return null;
        ContentResolver r = c.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, mime(name));
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/turk7filimo");
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri col = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri u = null;
        try {
            u = r.insert(col, v);
            if (u == null) return null;
            InputStream in = new FileInputStream(f);
            OutputStream out = r.openOutputStream(u);
            try {
                if (out == null) throw new Exception("no stream");
                copy(in, out);
            } finally {
                in.close();
                if (out != null) out.close();
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            r.update(u, done, null, null);
            putRec(c, name, u, f.length());
            return u;
        } catch (Exception e) {
            if (u != null) {
                try {
                    r.delete(u, null, null);
                } catch (Exception ignored) {
                }
            }
            return null;
        }
    }

    // ---------- گالری ← خصوصی ----------
    static boolean makePrivate(Context c, String name) {
        String us = uriOf(c, name);
        if (us == null) return false;
        ContentResolver r = c.getContentResolver();
        Uri u = Uri.parse(us);
        File out = new File(Store.dir(c), name);
        try {
            InputStream in = r.openInputStream(u);
            FileOutputStream fo = new FileOutputStream(out);
            try {
                if (in == null) throw new Exception("no stream");
                copy(in, fo);
            } finally {
                if (in != null) in.close();
                fo.close();
            }
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            return false;
        }
        try {
            r.delete(u, null, null);
        } catch (Exception ignored) {
        }
        removeRec(c, name);
        return true;
    }

    static void deletePublic(Context c, String name) {
        String us = uriOf(c, name);
        if (us != null) {
            try {
                c.getContentResolver().delete(Uri.parse(us), null, null);
            } catch (Exception ignored) {
            }
        }
        removeRec(c, name);
    }
}
