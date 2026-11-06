package ir.turk7filimo.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** پخش‌کننده‌ی اختصاصی: سرعت، زیرنویس (با تأخیر), صدا، کیفیت، حرکت‌های لمسی و ادامه از جای توقف */
public class PlayerActivity extends Activity {
    private static final int REQ_SUB = 77;
    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private PlayerView pv;
    private LinearLayout bar;
    private TextView hud;
    private AudioManager am;

    private String src, title, key;
    private boolean online;
    private int retries;
    private float speed = 1f;
    private File subFile;
    private long subOffset;
    private float volAcc = -1f;

    private final Runnable saver = new Runnable() {
        @Override
        public void run() {
            saveProgress();
            handler.postDelayed(this, 5000);
        }
    };
    private final Runnable hideHud = new Runnable() {
        @Override
        public void run() {
            if (hud != null) hud.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);

        src = getIntent().getStringExtra("src");
        if (src == null) {
            finish();
            return;
        }
        key = src;
        title = getIntent().getStringExtra("title");
        if (title == null || title.isEmpty()) {
            String last = Uri.parse(src).getLastPathSegment();
            title = Store.titleOf(last == null ? src : last);
        }
        online = src.startsWith("http");
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // زیرنویس ذخیره‌شده‌ی قبلی برای همین فیلم
        String savedSub = Store.prefs(this).getString("sub_" + key, null);
        if (savedSub != null && savedSub.contains("|")) {
            try {
                int i = savedSub.lastIndexOf('|');
                File f = new File(savedSub.substring(0, i));
                if (f.exists()) {
                    subFile = f;
                    subOffset = Long.parseLong(savedSub.substring(i + 1));
                }
            } catch (Exception ignored) {
            }
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        pv = new PlayerView(this);
        pv.setControllerShowTimeoutMs(3500);
        root.addView(pv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        styleSubtitles();

        buildTopBar(root);
        buildHud(root);
        setContentView(root);
        immersive();
        setupGestures();

        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", Store.HOME);
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(Store.UA)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, http)))
                .build();
        pv.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) retries = 0;
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                if (online && retries < 6) {
                    retries++;
                    Toast.makeText(PlayerActivity.this, "ارتباط قطع شد؛ دوباره تلاش می‌کنم…", Toast.LENGTH_SHORT).show();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (player != null) reloadItem();
                        }
                    }, 3500);
                } else {
                    Toast.makeText(PlayerActivity.this, "پخش ممکن نشد: " + e.getErrorCodeName(), Toast.LENGTH_LONG).show();
                }
            }
        });

        long start = Data.position(this, key);
        if (start > 5000) {
            Toast.makeText(this, "ادامه از " + Store.fmtTime(start), Toast.LENGTH_SHORT).show();
        } else {
            start = 0;
        }
        player.setMediaItem(buildItem(), start);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    // ---------- ظاهر ----------
    private void styleSubtitles() {
        SubtitleView sv = pv.getSubtitleView();
        if (sv == null) return;
        sv.setApplyEmbeddedStyles(false);
        sv.setApplyEmbeddedFontSizes(false);
        sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.15f);
        sv.setStyle(new CaptionStyleCompat(Color.WHITE, 0x99000000, Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_NONE, Color.WHITE, null));
    }

    private void buildTopBar(FrameLayout root) {
        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bar.setPadding(Store.dp(this, 14), Store.dp(this, 10), Store.dp(this, 14), Store.dp(this, 22));
        bar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xCC000000, 0x00000000}));

        bar.addView(Ui.iconBtn(this, Icons.CLOSE, Ui.TEXT, 0x66000000, 44, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }));
        TextView t = Ui.text(this, title, 15, Ui.TEXT, true);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        tl.setMarginStart(Store.dp(this, 12));
        tl.setMarginEnd(Store.dp(this, 12));
        bar.addView(t, tl);

        addBtn(Icons.SPEED, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickSpeed();
            }
        });
        addBtn(Icons.AUDIO, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickAudio();
            }
        });
        addBtn(Icons.HD, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickQuality();
            }
        });
        addBtn(Icons.CC, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickSubtitle();
            }
        });

        root.addView(bar, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.TOP));
        pv.setControllerVisibilityListener(new PlayerView.ControllerVisibilityListener() {
            @Override
            public void onVisibilityChanged(int visibility) {
                bar.setVisibility(visibility);
            }
        });
    }

    private void addBtn(int icon, View.OnClickListener l) {
        View b = Ui.iconBtn(this, icon, Ui.TEXT, 0x66000000, 44, l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Store.dp(this, 44), Store.dp(this, 44));
        lp.setMarginStart(Store.dp(this, 8));
        bar.addView(b, lp);
    }

    private void buildHud(FrameLayout root) {
        hud = Ui.text(this, "", 22, Ui.TEXT, true);
        hud.setGravity(Gravity.CENTER);
        hud.setPadding(Store.dp(this, 26), Store.dp(this, 14), Store.dp(this, 26), Store.dp(this, 14));
        hud.setBackground(Ui.shape(this, 0xAA000000, 30));
        hud.setVisibility(View.GONE);
        root.addView(hud, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
    }

    private void showHud(String s) {
        hud.setText(s);
        hud.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideHud);
        handler.postDelayed(hideHud, 800);
    }

    // ---------- حرکت‌های لمسی ----------
    private void setupGestures() {
        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                volAcc = -1f;
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (pv.isControllerFullyVisible()) pv.hideController();
                else pv.showController();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (player == null) return true;
                float w = pv.getWidth();
                if (e.getX() < w / 3f) {
                    player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
                    showHud("‹‹  ۱۰ ثانیه");
                } else if (e.getX() > w * 2f / 3f) {
                    long d = player.getDuration();
                    long to = player.getCurrentPosition() + 10000;
                    player.seekTo(d > 0 ? Math.min(d, to) : to);
                    showHud("۱۰ ثانیه  ››");
                } else if (player.isPlaying()) {
                    player.pause();
                    showHud("توقف");
                } else {
                    player.play();
                    showHud("پخش");
                }
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (e1 == null || Math.abs(dy) < Math.abs(dx)) return false;
                float h = Math.max(1f, pv.getHeight() * 0.8f);
                float delta = dy / h;
                if (e1.getX() < pv.getWidth() / 2f) {
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    float b = lp.screenBrightness < 0 ? 0.5f : lp.screenBrightness;
                    b = Math.max(0.02f, Math.min(1f, b + delta));
                    lp.screenBrightness = b;
                    getWindow().setAttributes(lp);
                    showHud("روشنایی  " + Math.round(b * 100) + "٪");
                } else if (am != null) {
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    if (volAcc < 0) volAcc = am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max;
                    volAcc = Math.max(0f, Math.min(1f, volAcc + delta));
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(volAcc * max), 0);
                    showHud("صدا  " + Math.round(volAcc * 100) + "٪");
                }
                return true;
            }
        });
        pv.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                gd.onTouchEvent(ev);
                return true;
            }
        });
    }

    // ---------- منبع و زیرنویس ----------
    private MediaItem buildItem() {
        MediaItem.Builder b = new MediaItem.Builder().setUri(Uri.parse(src));
        if (subFile != null && subFile.exists()) {
            File use = subFile;
            boolean vtt = subFile.getName().endsWith(".vtt");
            if (subOffset != 0 && !vtt) {
                try {
                    use = SubUtil.shift(this, subFile, subOffset);
                } catch (Exception ignored) {
                }
            }
            MediaItem.SubtitleConfiguration cfg = new MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(use))
                    .setMimeType(vtt ? MimeTypes.TEXT_VTT : MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage("fa")
                    .setLabel("زیرنویس بارگذاری‌شده")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build();
            b.setSubtitleConfigurations(Collections.singletonList(cfg));
        }
        return b.build();
    }

    private void reloadItem() {
        long p = player.getCurrentPosition();
        boolean play = player.getPlayWhenReady();
        player.setMediaItem(buildItem(), p);
        player.prepare();
        player.setPlayWhenReady(play);
    }

    private void persistSub() {
        if (subFile == null) return;
        Store.prefs(this).edit().putString("sub_" + key, subFile.getAbsolutePath() + "|" + subOffset).apply();
    }

    private void saveProgress() {
        if (player == null || key == null) return;
        long d = player.getDuration();
        long p = player.getCurrentPosition();
        if (d > 0 && p > 3000) Data.saveProgress(this, key, title, p, d);
    }

    // ---------- پنجره‌های انتخاب ----------
    private void pickSpeed() {
        List<String> labels = new ArrayList<>();
        int sel = 2;
        for (int i = 0; i < SPEEDS.length; i++) {
            labels.add(String.format(Locale.US, "%.2f×", SPEEDS[i]).replace(".00×", "×").replace("0×", "×"));
            if (Math.abs(SPEEDS[i] - speed) < 0.01f) sel = i;
        }
        Ui.choose(this, "سرعت پخش", labels, sel, new Ui.Pick() {
            @Override
            public void on(int i) {
                speed = SPEEDS[i];
                player.setPlaybackParameters(new PlaybackParameters(speed));
                showHud(String.format(Locale.US, "%.2f×", speed));
            }
        });
    }

    private static String trackLabel(Format f, int n) {
        if (f.label != null && !f.label.isEmpty()) return f.label;
        String lang = f.language;
        if (lang != null && !lang.isEmpty() && !"und".equals(lang)) {
            String name = new Locale(lang).getDisplayLanguage(new Locale("fa"));
            return (name == null || name.isEmpty()) ? lang : name;
        }
        return "ترک " + n;
    }

    private void pickAudio() {
        final List<Tracks.Group> groups = new ArrayList<>();
        final List<Integer> idx = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int sel = 0;
        for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
            if (g.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < g.length; i++) {
                Format f = g.getTrackFormat(i);
                String l = trackLabel(f, labels.size() + 1);
                if (f.channelCount > 2) l += "  (" + f.channelCount + " کاناله)";
                if (g.isTrackSelected(i)) sel = labels.size();
                labels.add(l);
                groups.add(g);
                idx.add(i);
            }
        }
        if (labels.size() < 2) {
            Toast.makeText(this, "این فایل فقط یک صدا دارد", Toast.LENGTH_SHORT).show();
            return;
        }
        Ui.choose(this, "صدا", labels, sel, new Ui.Pick() {
            @Override
            public void on(int i) {
                TrackSelectionParameters p = player.getTrackSelectionParameters().buildUpon()
                        .setOverrideForType(new TrackSelectionOverride(groups.get(i).getMediaTrackGroup(), idx.get(i)))
                        .build();
                player.setTrackSelectionParameters(p);
            }
        });
    }

    private void pickQuality() {
        final List<Tracks.Group> groups = new ArrayList<>();
        final List<Integer> idx = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        labels.add("خودکار");
        boolean auto = true;
        int sel = 0;
        for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
            if (g.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < g.length; i++) {
                Format f = g.getTrackFormat(i);
                if (f.height <= 0) continue;
                labels.add(f.height + "p");
                groups.add(g);
                idx.add(i);
            }
        }
        if (labels.size() < 3) {
            Toast.makeText(this, "این فایل فقط یک کیفیت دارد", Toast.LENGTH_SHORT).show();
            return;
        }
        Ui.choose(this, "کیفیت تصویر", labels, sel, new Ui.Pick() {
            @Override
            public void on(int i) {
                TrackSelectionParameters.Builder b = player.getTrackSelectionParameters().buildUpon();
                if (i == 0) b.clearOverridesOfType(C.TRACK_TYPE_VIDEO);
                else b.setOverrideForType(new TrackSelectionOverride(groups.get(i - 1).getMediaTrackGroup(), idx.get(i - 1)));
                player.setTrackSelectionParameters(b.build());
            }
        });
    }

    private void pickSubtitle() {
        final List<Tracks.Group> groups = new ArrayList<>();
        final List<Integer> idx = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        labels.add("بدون زیرنویس");
        int sel = 0;
        boolean textOff = player.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
        for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
            if (g.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < g.length; i++) {
                if (g.isTrackSelected(i) && !textOff) sel = labels.size();
                labels.add(trackLabel(g.getTrackFormat(i), labels.size()));
                groups.add(g);
                idx.add(i);
            }
        }
        final int tracksEnd = labels.size();
        labels.add("📂  انتخاب فایل زیرنویس (srt یا vtt)");
        final boolean canShift = subFile != null && !subFile.getName().endsWith(".vtt");
        if (canShift) {
            labels.add("زیرنویس را ۰٫۵ ثانیه زودتر کن");
            labels.add("زیرنویس را ۰٫۵ ثانیه دیرتر کن");
            labels.add(String.format(Locale.US, "بازنشانی تأخیر (الان %+.1f ثانیه)", subOffset / 1000.0));
        }
        Ui.choose(this, "زیرنویس", labels, sel, new Ui.Pick() {
            @Override
            public void on(int i) {
                if (i == 0) {
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build());
                } else if (i < tracksEnd) {
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(new TrackSelectionOverride(groups.get(i - 1).getMediaTrackGroup(), idx.get(i - 1)))
                            .build());
                } else if (i == tracksEnd) {
                    Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    it.addCategory(Intent.CATEGORY_OPENABLE);
                    it.setType("*/*");
                    startActivityForResult(it, REQ_SUB);
                } else if (canShift) {
                    int k = i - tracksEnd;
                    if (k == 1) subOffset -= 500;
                    else if (k == 2) subOffset += 500;
                    else subOffset = 0;
                    persistSub();
                    reloadItem();
                    showHud(String.format(Locale.US, "تأخیر زیرنویس %+.1f ثانیه", subOffset / 1000.0));
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_SUB || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri u = data.getData();
        String nm = null;
        Cursor cur = null;
        try {
            cur = getContentResolver().query(u, null, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                int ix = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (ix >= 0) nm = cur.getString(ix);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        final String name = nm == null ? "" : nm;
        String low = name.toLowerCase(Locale.ROOT);
        if (low.endsWith(".ass") || low.endsWith(".ssa") || low.endsWith(".sub")) {
            Toast.makeText(this, "فقط زیرنویس srt و vtt پشتیبانی می‌شود", Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final File f = SubUtil.importSub(PlayerActivity.this, u, name);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            subFile = f;
                            subOffset = 0;
                            persistSub();
                            if (player != null) {
                                player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build());
                                reloadItem();
                            }
                            Toast.makeText(PlayerActivity.this, "زیرنویس بارگذاری شد", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(PlayerActivity.this, "خواندن زیرنویس ممکن نشد", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    // ---------- چرخه‌ی حیات ----------
    @SuppressWarnings("deprecation")
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(saver, 5000);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(saver);
        if (player != null) {
            player.pause();
            saveProgress();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
