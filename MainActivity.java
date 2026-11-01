package ir.turk7filimo.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int MATCH = Ui.MATCH, WRAP = Ui.WRAP;

    private static final String FIND_VIDEOS_JS =
            "(function(){var r=[];function add(u){if(u&&r.indexOf(u)<0)r.push(u);}"
                    + "document.querySelectorAll('video,source').forEach(function(e){add(e.currentSrc||e.src);});"
                    + "document.querySelectorAll('a[href]').forEach(function(a){"
                    + "if(/\\.(mkv|mp4|webm|avi|mov|m4v|3gp)(\\?|#|$)/i.test(a.href))add(a.href);});"
                    + "return JSON.stringify(r);})()";

    private static final String ERROR_HTML =
            "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                    + "<body style='margin:0;background:#0E0A1C;color:#fff;font-family:sans-serif;display:flex;"
                    + "flex-direction:column;align-items:center;justify-content:center;height:100vh;text-align:center;direction:rtl'>"
                    + "<div style='font-size:56px'>📡</div><h3>اتصال برقرار نشد</h3>"
                    + "<p style='color:#B9ABDD'>اینترنت را بررسی کن و دوباره امتحان کن</p>"
                    + "<a href='" + Store.HOME + "' style='margin-top:12px;padding:12px 30px;border-radius:30px;"
                    + "background:linear-gradient(90deg,#A259FF,#7B1FFF);color:#fff;text-decoration:none;font-weight:bold'>"
                    + "تلاش دوباره</a></body></html>";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private ProgressBar bar;
    private FrameLayout splash;
    private ImageView splashLogo;
    private ObjectAnimator pulse;
    private boolean splashGone;
    private TextView badge;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;

    private final Runnable hideSplashLater = new Runnable() {
        @Override
        public void run() {
            hideSplash();
        }
    };

    private final Runnable badgeTick = new Runnable() {
        @Override
        public void run() {
            int n = DownloadService.active.size();
            if (badge != null) {
                badge.setText(String.valueOf(n));
                badge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
            }
            handler.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.SURFACE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Ui.BG);

        FrameLayout top = new FrameLayout(this);
        web = new WebView(this);
        web.setBackgroundColor(Ui.BG);
        top.addView(web, new FrameLayout.LayoutParams(MATCH, MATCH));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(Ui.P1));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x00000000));
        top.addView(bar, new FrameLayout.LayoutParams(MATCH, Store.dp(this, 3), Gravity.TOP));

        buildSplash();
        top.addView(splash, new FrameLayout.LayoutParams(MATCH, MATCH));

        root.addView(top, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        root.addView(buildNav(), new LinearLayout.LayoutParams(MATCH, WRAP));
        setContentView(root);

        setupWeb();
        web.loadUrl(Store.HOME);
        handler.postDelayed(hideSplashLater, 9000);
    }

    // ---------- ظاهر ----------

    private void buildSplash() {
        splash = new FrameLayout(this);
        splash.setBackgroundColor(Ui.BG);
        splash.setClickable(true);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        splashLogo = Ui.logo(this, 130);
        c.addView(splashLogo);
        TextView name = Ui.text(this, "turk7filimk", 16, Ui.MUTED, true);
        name.setPadding(0, Store.dp(this, 12), 0, 0);
        c.addView(name, new LinearLayout.LayoutParams(WRAP, WRAP));
        splash.addView(c, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER));
        pulse = ObjectAnimator.ofFloat(splashLogo, "alpha", 0.45f, 1f);
        pulse.setDuration(900);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.start();
    }

    private void hideSplash() {
        if (splashGone) return;
        splashGone = true;
        if (pulse != null) pulse.cancel();
        splash.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
            @Override
            public void run() {
                splash.setVisibility(View.GONE);
            }
        }).start();
    }

    private View buildNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SURFACE);
        float r = Store.dp(this, 22);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setStroke(Store.dp(this, 1), 0x33A259FF);
        nav.setBackground(bg);
        nav.setPadding(Store.dp(this, 8), Store.dp(this, 6), Store.dp(this, 8), Store.dp(this, 4));

        nav.addView(tab(Icons.HOME, "خانه", false, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                web.loadUrl(Store.HOME);
            }
        }), new LinearLayout.LayoutParams(0, WRAP, 1f));
        nav.addView(tab(Icons.FILM, "ویدیوهای صفحه", true, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickVideos();
            }
        }), new LinearLayout.LayoutParams(0, WRAP, 1.3f));
        nav.addView(tab(Icons.DOWNLOAD, "دانلودها", false, true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DownloadsActivity.class));
            }
        }), new LinearLayout.LayoutParams(0, WRAP, 1f));
        nav.addView(tab(Icons.REFRESH, "رفرش", false, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                web.reload();
            }
        }), new LinearLayout.LayoutParams(0, WRAP, 1f));
        return nav;
    }

    private View tab(int icon, String label, boolean hot, boolean withBadge, View.OnClickListener l) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(0, Store.dp(this, 4), 0, Store.dp(this, 2));
        t.setClickable(true);
        t.setOnClickListener(l);
        t.setBackground(Ui.ripple(this, Ui.shape(this, 0x00000000, 14), 14));

        FrameLayout box = new FrameLayout(this);
        if (hot) box.setBackground(Ui.grad(this, 16));
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(Icons.get(icon, hot ? Ui.TEXT : Ui.MUTED));
        int s = Store.dp(this, 24);
        box.addView(ic, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        if (withBadge) {
            badge = Ui.text(this, "0", 10, Ui.TEXT, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(Ui.circle(Ui.DANGER));
            badge.setVisibility(View.GONE);
            int bs = Store.dp(this, 18);
            box.addView(badge, new FrameLayout.LayoutParams(bs, bs, Gravity.TOP | Gravity.END));
        }
        t.addView(box, new LinearLayout.LayoutParams(Store.dp(this, hot ? 60 : 48), Store.dp(this, 32)));

        TextView tv = Ui.text(this, label, 11, hot ? Ui.TEXT : Ui.MUTED, hot);
        tv.setPadding(0, Store.dp(this, 3), 0, 0);
        tv.setSingleLine(true);
        t.addView(tv, new LinearLayout.LayoutParams(WRAP, WRAP));
        return t;
    }

    // ---------- وب‌ویو ----------

    private void setupWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String u = req.getUrl().toString();
                if (Store.isVideoUrl(u)) {
                    askVideo(u, Store.nameFromUrl(u));
                    return true;
                }
                String sch = req.getUrl().getScheme();
                if (!"http".equals(sch) && !"https".equals(sch)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, req.getUrl()));
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                bar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                bar.setVisibility(View.GONE);
                hideSplash();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    hideSplash();
                    view.loadDataWithBaseURL(null, ERROR_HTML, "text/html", "UTF-8", null);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onShowCustomView(View v, CustomViewCallback cb) {
                if (customView != null) {
                    cb.onCustomViewHidden();
                    return;
                }
                customView = v;
                customCb = cb;
                ((FrameLayout) getWindow().getDecorView()).addView(v, new FrameLayout.LayoutParams(MATCH, MATCH));
                fullscreen(true);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                ((FrameLayout) getWindow().getDecorView()).removeView(customView);
                customView = null;
                if (customCb != null) customCb.onCustomViewHidden();
                fullscreen(false);
            }
        });

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String u, String ua, String cd, String mime, long len) {
                String name = Store.isVideoUrl(u) ? Store.nameFromUrl(u) : URLUtil.guessFileName(u, cd, mime);
                askVideo(u, name);
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void fullscreen(boolean on) {
        if (on) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    // ---------- پنجره‌ی دانلود / پخش ----------

    private void askVideo(final String url, final String name) {
        LinearLayout card = Ui.card(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(Ui.logo(this, 60));

        TextView title = Ui.text(this, "این ویدیو را چه کنم؟", 17, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Store.dp(this, 10), 0, 0);
        card.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView nm = Ui.text(this, name, 12, Ui.MUTED, false);
        nm.setGravity(Gravity.CENTER);
        nm.setMaxLines(2);
        nm.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        nm.setPadding(0, Store.dp(this, 4), 0, Store.dp(this, 16));
        card.addView(nm, new LinearLayout.LayoutParams(MATCH, WRAP));

        final Dialog d = Ui.sheet(this, card);
        card.addView(Ui.pill(this, "دانلود", Icons.DOWNLOAD, Ui.K_PRIMARY, 52, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                Store.startDownload(MainActivity.this, url, name);
            }
        }), Ui.fill(this, 10));
        card.addView(Ui.pill(this, "پخش آنلاین", Icons.PLAY, Ui.K_SECONDARY, 52, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                startActivity(new Intent(MainActivity.this, PlayerActivity.class).putExtra("src", url));
            }
        }), Ui.fill(this, 6));

        TextView cancel = Ui.text(this, "لغو", 14, Ui.MUTED, false);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, Store.dp(this, 10), 0, Store.dp(this, 4));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        card.addView(cancel, new LinearLayout.LayoutParams(MATCH, WRAP));
        d.show();
    }

    private void noVideos() {
        Toast.makeText(this, "لینک مستقیم ویدیو در این صفحه پیدا نشد", Toast.LENGTH_LONG).show();
    }

    private void pickVideos() {
        web.evaluateJavascript(FIND_VIDEOS_JS, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                try {
                    if (value == null || value.equals("null")) {
                        noVideos();
                        return;
                    }
                    Object o = new JSONTokener(value).nextValue();
                    JSONArray arr = new JSONArray((String) o);
                    List<String> urls = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        String u = arr.getString(i);
                        if (u.startsWith("http")) urls.add(u);
                    }
                    if (urls.isEmpty()) {
                        noVideos();
                        return;
                    }
                    showVideoSheet(urls);
                } catch (Exception e) {
                    noVideos();
                }
            }
        });
    }

    private void showVideoSheet(List<String> urls) {
        LinearLayout card = Ui.card(this);
        TextView title = Ui.text(this, "ویدیوهای این صفحه", 17, Ui.TEXT, true);
        title.setPadding(0, 0, 0, Store.dp(this, 12));
        card.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));

        final Dialog d = Ui.sheet(this, card);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (final String u : urls) {
            final String nm = Store.nameFromUrl(u);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(Ui.shape(this, Ui.SURFACE2, 18));
            row.setPadding(Store.dp(this, 12), Store.dp(this, 12), Store.dp(this, 12), Store.dp(this, 12));

            TextView n = Ui.text(this, nm, 13, Ui.TEXT, false);
            n.setMaxLines(2);
            n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            n.setPadding(0, 0, 0, Store.dp(this, 10));
            row.addView(n, new LinearLayout.LayoutParams(MATCH, WRAP));

            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            View dl = Ui.pill(this, "دانلود", Icons.DOWNLOAD, Ui.K_PRIMARY, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    d.dismiss();
                    Store.startDownload(MainActivity.this, u, nm);
                }
            });
            View pl = Ui.pill(this, "پخش", Icons.PLAY, Ui.K_SECONDARY, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    d.dismiss();
                    startActivity(new Intent(MainActivity.this, PlayerActivity.class).putExtra("src", u));
                }
            });
            LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, WRAP, 1f);
            a.setMarginEnd(Store.dp(this, 8));
            btns.addView(dl, a);
            btns.addView(pl, new LinearLayout.LayoutParams(0, WRAP, 1f));
            row.addView(btns, new LinearLayout.LayoutParams(MATCH, WRAP));
            list.addView(row, Ui.fill(this, 10));
        }

        ScrollView sv = new ScrollView(this);
        sv.addView(list, new FrameLayout.LayoutParams(MATCH, WRAP));
        int h = Math.min(urls.size() * 120, 400);
        card.addView(sv, new LinearLayout.LayoutParams(MATCH, Store.dp(this, h)));
        d.show();
    }

    // ---------- چرخه‌ی حیات ----------

    @Override
    public void onBackPressed() {
        if (customView != null) {
            web.getWebChromeClient().onHideCustomView();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        handler.post(badgeTick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(badgeTick);
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(hideSplashLater);
        if (pulse != null) pulse.cancel();
        web.destroy();
        super.onDestroy();
    }
}
