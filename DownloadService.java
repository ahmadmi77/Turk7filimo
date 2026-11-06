package ir.turk7filimo.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadService extends Service {
    static final String EXTRA_URL = "url";
    static final String EXTRA_NAME = "name";
    private static final String CH = "dl";

    /** نام فایل‌هایی که الان در حال دانلودند */
    static final Set<String> active = Collections.synchronizedSet(new HashSet<String>());
    /** درخواست توقف */
    static final Set<String> cancelled = Collections.synchronizedSet(new HashSet<String>());
    /** درخواست توقف و حذف */
    static final Set<String> toDelete = Collections.synchronizedSet(new HashSet<String>());

    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final AtomicInteger running = new AtomicInteger(0);

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CH, "دانلودها", NotificationManager.IMPORTANCE_LOW));
        }
        Notification n = build("آماده‌سازی دانلود…", 0, true);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, n);
        }

        final String url = intent == null ? null : intent.getStringExtra(EXTRA_URL);
        final String name = intent == null ? null : intent.getStringExtra(EXTRA_NAME);
        if (url == null || name == null) {
            if (running.get() == 0) {
                stopForeground(true);
                stopSelf();
            }
            return START_NOT_STICKY;
        }

        running.incrementAndGet();
        pool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    download(url, name);
                } finally {
                    active.remove(name);
                    if (running.decrementAndGet() == 0) {
                        stopForeground(true);
                        stopSelf();
                    }
                }
            }
        });
        return START_NOT_STICKY;
    }

    private Notification.Builder builder(boolean ongoing) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH)
                : new Notification.Builder(this);
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, DownloadsActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        b.setContentTitle("turk7filimo").setContentIntent(pi).setOngoing(ongoing).setOnlyAlertOnce(true);
        return b;
    }

    private Notification build(String text, int pct, boolean indeterminate) {
        Notification.Builder b = builder(true);
        b.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentText(text)
                .setProgress(100, pct, indeterminate);
        return b.build();
    }

    private void finalNotice(NotificationManager nm, String name, boolean ok, String text) {
        Notification.Builder b = builder(false);
        b.setSmallIcon(ok ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_notify_error)
                .setContentText(text)
                .setAutoCancel(true);
        nm.notify(1000 + (name.hashCode() & 0xffff), b.build());
    }

    private HttpURLConnection open(String url, long have) throws IOException {
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", Store.UA);
            c.setRequestProperty("Referer", Store.HOME);
            c.setRequestProperty("Accept-Encoding", "identity");
            if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
            int code = c.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("redirect without location");
                url = new URL(new URL(url), loc).toString();
                continue;
            }
            return c;
        }
        throw new IOException("too many redirects");
    }

    private void clearPrefs(String name) {
        Store.prefs(this).edit().remove("total_" + name).remove("url_" + name)
                .remove("pub_" + name).remove("paused_" + name).apply();
    }

    /** اگر کاربر توقف یا حذف خواسته باشد، آن را اعمال می‌کند و true برمی‌گرداند */
    private boolean stopRequested(String name, File part) {
        if (!cancelled.contains(name)) return false;
        cancelled.remove(name);
        if (toDelete.remove(name)) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            clearPrefs(name);
        } else {
            Store.prefs(this).edit().putBoolean("paused_" + name, true).apply();
        }
        return true;
    }

    /** منتظر می‌ماند؛ اگر در این بین کاربر توقف بخواهد false برمی‌گرداند */
    private boolean pause(String name, File part, long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (stopRequested(name, part)) return false;
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return true;
    }

    private void download(String url, String name) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        File part = new File(Store.dir(this), name + ".part");
        int fails = 0;
        while (true) {
            if (stopRequested(name, part)) return;

            if (!Store.netOk(this)) {
                String why = Store.online(this) ? "منتظر Wi‑Fi…" : "منتظر اینترنت…";
                nm.notify(1, build(name + " — " + why, 0, true));
                if (!pause(name, part, 2500)) return;
                continue;
            }

            long before = part.exists() ? part.length() : 0;
            try {
                int r = attempt(url, name, part, nm);
                if (r == 2) return;
                if (r == 1) {
                    complete(name, part, nm);
                    return;
                }
            } catch (Exception e) {
                if (part.exists() && part.length() > before) fails = 0;
                else fails++;
                if (fails > 40) {
                    finalNotice(nm, name, false, "دانلود ناموفق بود: " + e.getMessage());
                    return;
                }
                nm.notify(1, build(name + " — ارتباط قطع شد، دوباره تلاش می‌کنم…", 0, true));
                if (!pause(name, part, Math.min(20000L, 1500L * (fails + 1)))) return;
            }
        }
    }

    /** ۱ = کامل شد، ۲ = کاربر متوقف کرد. اگر ناقص بماند استثنا می‌دهد تا دوباره از همان بایت ادامه پیدا کند. */
    private int attempt(String url, String name, File part, NotificationManager nm) throws IOException {
        long have = part.exists() ? part.length() : 0;
        HttpURLConnection c = open(url, have);
        try {
            int code = c.getResponseCode();
            long len = c.getContentLengthLong();
            boolean append;
            long total;
            if (code == 206) {
                append = true;
                total = len > 0 ? have + len : -1;
            } else if (code == 200) {
                append = false;
                have = 0;
                total = len;
            } else if (code == 416) {
                long known = Store.prefs(this).getLong("total_" + name, -1);
                if (have > 0 && known > 0 && have >= known) return 1;
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                throw new IOException("HTTP 416");
            } else {
                throw new IOException("HTTP " + code);
            }
            Store.prefs(this).edit().putLong("total_" + name, total).apply();

            long done = have;
            long lastTick = 0;
            boolean stopped = false;
            InputStream in = c.getInputStream();
            FileOutputStream fo = new FileOutputStream(part, append);
            try {
                byte[] buf = new byte[65536];
                int r;
                while ((r = in.read(buf)) != -1) {
                    if (cancelled.contains(name)) {
                        stopped = true;
                        break;
                    }
                    fo.write(buf, 0, r);
                    done += r;
                    long now = System.currentTimeMillis();
                    if (now - lastTick > 1000) {
                        lastTick = now;
                        int pct = total > 0 ? (int) (done * 100 / total) : 0;
                        nm.notify(1, build(name + " — " + pct + "%", pct, total <= 0));
                    }
                }
            } finally {
                try {
                    fo.close();
                } catch (IOException ignored) {
                }
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
            if (stopped) {
                stopRequested(name, part);
                return 2;
            }
            if (total > 0 && part.length() < total) throw new IOException("فایل ناقص دریافت شد");
            return 1;
        } finally {
            c.disconnect();
        }
    }

    private void complete(String name, File part, NotificationManager nm) {
        File out = new File(Store.dir(this), name);
        //noinspection ResultOfMethodCallIgnored
        part.renameTo(out);
        boolean pub = Store.prefs(this).getBoolean("pub_" + name, false);
        String where = "";
        if (pub && Publisher.supported()) {
            Uri u = Publisher.publish(this, out, name);
            if (u != null) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
                where = " (در گالری)";
            }
        }
        clearPrefs(name);
        finalNotice(nm, name, true, "دانلود کامل شد: " + name + where);
    }

    @Override
    public void onDestroy() {
        pool.shutdown();
        super.onDestroy();
    }
}
