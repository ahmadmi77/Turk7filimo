package ir.turk7filimo.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.widget.Toast;

import java.io.File;
import java.net.URLDecoder;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Store {
    static final String HOME = "https://www.turk7filimo.ir/";
    static final String UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final Pattern QUALITY = Pattern.compile("(?<![0-9])(2160|1440|1080|720|576|480|360|240)(?![0-9])");

    private Store() {}

    /** پوشه خصوصی اپ: در گالری و فایل‌منیجر عمومی دیده نمی‌شود و با حذف اپ پاک می‌شود */
    static File dir(Context c) {
        File d = c.getExternalFilesDir("movies");
        if (d == null) d = new File(c.getFilesDir(), "movies");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        try {
            File nm = new File(d, ".nomedia");
            if (!nm.exists()) //noinspection ResultOfMethodCallIgnored
                nm.createNewFile();
        } catch (Exception ignored) {
        }
        return d;
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("dl", Context.MODE_PRIVATE);
    }

    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    // ---------- شبکه ----------
    private static ConnectivityManager cm(Context c) {
        return (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    static boolean online(Context c) {
        ConnectivityManager m = cm(c);
        if (m == null) return true;
        Network n = m.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities nc = m.getNetworkCapabilities(n);
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /** اینترنت هست و اگر «فقط Wi‑Fi» روشن است، به شبکه‌ی بدون‌حجم وصل است */
    static boolean netOk(Context c) {
        if (!online(c)) return false;
        ConnectivityManager m = cm(c);
        if (Data.wifiOnly(c) && m != null && m.isActiveNetworkMetered()) return false;
        return true;
    }

    // ---------- کیفیت هوشمند ----------
    static int qualityOf(String s) {
        if (s == null) return 0;
        Matcher m = QUALITY.matcher(s);
        int last = 0;
        while (m.find()) last = Integer.parseInt(m.group(1));
        return last;
    }

    /** بیشترین ارتفاع تصویری که با سرعت فعلی اینترنت منطقی است */
    static int targetHeight(Context c) {
        ConnectivityManager m = cm(c);
        int kbps = 0;
        boolean metered = false;
        if (m != null) {
            metered = m.isActiveNetworkMetered();
            Network n = m.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : m.getNetworkCapabilities(n);
            if (nc != null) kbps = nc.getLinkDownstreamBandwidthKbps();
        }
        int t;
        if (kbps <= 0) t = metered ? 480 : 720;
        else if (kbps >= 20000) t = 1080;
        else if (kbps >= 8000) t = 720;
        else if (kbps >= 3000) t = 480;
        else t = 360;
        if (metered && t > 480 && kbps < 40000) t = 480;
        return t;
    }

    /** ایندکس بهترین لینک از نظر کیفیت (یا -۱ اگر کیفیت لینک‌ها مشخص نیست) */
    static int recommend(Context c, List<String> urls) {
        int target = targetHeight(c);
        int best = -1, bestQ = 0, low = -1, lowQ = Integer.MAX_VALUE;
        for (int i = 0; i < urls.size(); i++) {
            int q = qualityOf(urls.get(i));
            if (q <= 0) continue;
            if (q <= target && q > bestQ) {
                best = i;
                bestQ = q;
            }
            if (q < lowQ) {
                low = i;
                lowQ = q;
            }
        }
        return best >= 0 ? best : low;
    }

    // ---------- نام و زمان ----------
    private static String stripQuery(String url) {
        String p = url;
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);
        int h = p.indexOf('#');
        if (h >= 0) p = p.substring(0, h);
        return p;
    }

    static boolean isVideoUrl(String url) {
        if (url == null) return false;
        String p = stripQuery(url).toLowerCase(Locale.ROOT);
        return p.endsWith(".mkv") || p.endsWith(".mp4") || p.endsWith(".webm")
                || p.endsWith(".avi") || p.endsWith(".mov") || p.endsWith(".m4v")
                || p.endsWith(".3gp");
    }

    static String nameFromUrl(String url) {
        String p = stripQuery(url);
        int s = p.lastIndexOf('/');
        String n = s >= 0 ? p.substring(s + 1) : p;
        try {
            n = URLDecoder.decode(n, "UTF-8");
        } catch (Exception ignored) {
        }
        n = n.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (n.isEmpty()) {
            n = "video_" + System.currentTimeMillis() + ".mp4";
        } else if (!n.matches(".*\\.[A-Za-z0-9]{2,4}$")) {
            n = n + ".mp4";
        }
        return n;
    }

    static String titleOf(String name) {
        String t = name == null ? "" : name;
        int d = t.lastIndexOf('.');
        if (d > 0 && t.length() - d <= 5) t = t.substring(0, d);
        return t;
    }

    static String fmtTime(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.US, "%02d:%02d", m, sec);
    }

    static String host(String url) {
        try {
            String h = new java.net.URL(url).getHost();
            return h == null ? url : h;
        } catch (Exception e) {
            return url;
        }
    }

    // ---------- دانلود ----------
    static void startDownload(Activity a, String url, String name, boolean pub) {
        if (new File(dir(a), name).exists() || Publisher.isPublic(a, name)) {
            Toast.makeText(a, "این فایل قبلاً دانلود شده", Toast.LENGTH_SHORT).show();
            return;
        }
        DownloadService.cancelled.remove(name);
        DownloadService.toDelete.remove(name);
        if (!DownloadService.active.add(name)) {
            Toast.makeText(a, "این فایل در حال دانلود است", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        prefs(a).edit().putString("url_" + name, url)
                .putBoolean("pub_" + name, pub && Publisher.supported())
                .remove("paused_" + name).apply();
        launch(a, url, name);
        Toast.makeText(a, "دانلود شروع شد", Toast.LENGTH_SHORT).show();
    }

    private static void launch(Context c, String url, String name) {
        Intent i = new Intent(c, DownloadService.class)
                .putExtra(DownloadService.EXTRA_URL, url)
                .putExtra(DownloadService.EXTRA_NAME, name);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception e) {
            DownloadService.active.remove(name);
        }
    }

    /** دانلودهای نیمه‌کاره‌ای که کاربر خودش متوقف نکرده، با باز شدن اپ ادامه پیدا می‌کنند */
    static void resumePending(Context c) {
        File[] fs = dir(c).listFiles();
        if (fs == null) return;
        SharedPreferences p = prefs(c);
        for (File f : fs) {
            String n = f.getName();
            if (!n.endsWith(".part")) continue;
            String name = n.substring(0, n.length() - 5);
            String url = p.getString("url_" + name, null);
            if (url == null || p.getBoolean("paused_" + name, false)) continue;
            if (!DownloadService.active.add(name)) continue;
            launch(c, url, name);
        }
    }
}
