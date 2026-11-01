package ir.turk7filimo.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DownloadsActivity extends Activity {
    private static final int MATCH = Ui.MATCH, WRAP = Ui.WRAP;

    static class Item {
        File f;
        String name;
        boolean part;
        boolean active;
        long size;
        long total;
        long speed;
        String url;
    }

    /** نوار پیشرفت گرد با گرادیان بنفش */
    static class ProgressLine extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float frac;

        ProgressLine(Context c) {
            super(c);
            track.setColor(Ui.SURFACE2);
        }

        void set(float f) {
            frac = Math.max(0f, Math.min(1f, f));
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            fill.setShader(new LinearGradient(0, 0, w, 0, Ui.P1, Ui.P2, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            rect.set(0, 0, w, h);
            c.drawRoundRect(rect, r, r, track);
            if (frac > 0) {
                float fw = Math.max(h, w * frac);
                if (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) rect.set(w - fw, 0, w, h);
                else rect.set(0, 0, fw, h);
                c.drawRoundRect(rect, r, r, fill);
            }
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Item> items = new ArrayList<>();
    private final Map<String, long[]> speedMap = new HashMap<>();
    private BaseAdapter adapter;
    private View empty;
    private TextView subtitle;
    private String lastSig = "";

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Ui.BG);

        // سربرگ
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(12), dp(14), dp(10));
        header.addView(Ui.iconBtn(this, Icons.BACK, Ui.TEXT, Ui.SURFACE2, 44, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(34), dp(34));
        llp.setMarginStart(dp(12));
        header.addView(Ui.logo(this, 34), llp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, "دانلودهای من", 18, Ui.TEXT, true));
        subtitle = Ui.text(this, "", 12, Ui.MUTED, false);
        col.addView(subtitle);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        clp.setMarginStart(dp(10));
        header.addView(col, clp);

        header.addView(Ui.pill(this, "لینک", Icons.ADD, Ui.K_PRIMARY, 44, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askLink();
            }
        }), new LinearLayout.LayoutParams(dp(112), WRAP));
        root.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));

        // بدنه
        FrameLayout body = new FrameLayout(this);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setDividerHeight(dp(10));
        lv.setPadding(dp(12), dp(4), dp(12), dp(16));
        lv.setClipToPadding(false);
        lv.setSelector(new ColorDrawable(0));
        lv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        lv.setVerticalScrollBarEnabled(false);
        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return items.size();
            }

            @Override
            public Object getItem(int p) {
                return items.get(p);
            }

            @Override
            public long getItemId(int p) {
                return p;
            }

            @Override
            public View getView(int p, View cv, ViewGroup parent) {
                Row r;
                if (cv == null) {
                    r = new Row();
                    cv = r.root;
                    cv.setTag(r);
                } else {
                    r = (Row) cv.getTag();
                }
                r.bind(items.get(p));
                return cv;
            }
        };
        lv.setAdapter(adapter);
        body.addView(lv, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout em = new LinearLayout(this);
        em.setOrientation(LinearLayout.VERTICAL);
        em.setGravity(Gravity.CENTER_HORIZONTAL);
        em.setPadding(dp(32), dp(70), dp(32), 0);
        ImageView big = Ui.logo(this, 110);
        big.setAlpha(0.55f);
        em.addView(big);
        TextView t1 = Ui.text(this, "هنوز چیزی دانلود نشده", 17, Ui.TEXT, true);
        t1.setGravity(Gravity.CENTER);
        t1.setPadding(0, dp(14), 0, dp(6));
        em.addView(t1, new LinearLayout.LayoutParams(MATCH, WRAP));
        TextView t2 = Ui.text(this, "روی لینک فیلم در سایت بزن و «دانلود» را انتخاب کن، یا از دکمه‌ی «لینک» بالا استفاده کن.", 13, Ui.MUTED, false);
        t2.setGravity(Gravity.CENTER);
        em.addView(t2, new LinearLayout.LayoutParams(MATCH, WRAP));
        empty = em;
        body.addView(em, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP));

        root.addView(body, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        setContentView(root);
    }

    private int dp(int v) {
        return Store.dp(this, v);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    // ---------- ردیف هر فایل ----------

    private class Row {
        final LinearLayout root;
        final TextView name;
        final TextView status;
        final ProgressLine prog;
        final LinearLayout actions;
        Item item;
        String key = "";

        Row() {
            Context c = DownloadsActivity.this;
            root = new LinearLayout(c);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackground(Ui.shape(c, Ui.SURFACE, 22));
            root.setPadding(dp(12), dp(12), dp(12), dp(12));

            LinearLayout top = new LinearLayout(c);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            FrameLayout tile = new FrameLayout(c);
            tile.setBackground(Ui.shape(c, Ui.BADGE, 16));
            ImageView lg = new ImageView(c);
            lg.setImageDrawable(c.getDrawable(R.drawable.logo));
            tile.addView(lg, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
            top.addView(tile, new LinearLayout.LayoutParams(dp(60), dp(60)));

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            name = Ui.text(c, "", 14, Ui.TEXT, true);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            status = Ui.text(c, "", 12, Ui.MUTED, false);
            status.setPadding(0, dp(4), 0, 0);
            col.addView(name, new LinearLayout.LayoutParams(MATCH, WRAP));
            col.addView(status, new LinearLayout.LayoutParams(MATCH, WRAP));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            clp.setMarginStart(dp(12));
            top.addView(col, clp);
            root.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

            prog = new ProgressLine(c);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(MATCH, dp(6));
            plp.topMargin = dp(10);
            root.addView(prog, plp);

            actions = new LinearLayout(c);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(MATCH, WRAP);
            alp.topMargin = dp(10);
            root.addView(actions, alp);
        }

        void bind(Item it) {
            Context c = DownloadsActivity.this;
            item = it;
            name.setText(it.name);
            float frac = it.total > 0 ? (float) it.size / it.total : 0f;
            String sz = Formatter.formatFileSize(c, it.size);
            if (!it.part) {
                status.setText("کامل شد  •  " + sz);
                status.setTextColor(Ui.OK);
                prog.setVisibility(View.GONE);
            } else {
                prog.setVisibility(View.VISIBLE);
                prog.set(frac);
                String pct = it.total > 0 ? ((int) (frac * 100)) + "%" : "";
                String tot = it.total > 0 ? " از " + Formatter.formatFileSize(c, it.total) : "";
                if (it.active) {
                    String sp = it.speed > 0 ? "  •  " + Formatter.formatFileSize(c, it.speed) + "/s" : "";
                    status.setText("در حال دانلود  " + pct + "  •  " + sz + tot + sp);
                    status.setTextColor(Ui.P1);
                } else {
                    status.setText("متوقف شده  " + pct + "  •  " + sz + tot);
                    status.setTextColor(Ui.MUTED);
                }
            }
            String k = it.part ? (it.active ? "A" : "P") : "C";
            if (!k.equals(key)) {
                key = k;
                rebuildActions();
            }
        }

        void rebuildActions() {
            Context c = DownloadsActivity.this;
            actions.removeAllViews();
            String label;
            int icon;
            if (!item.part) {
                label = "پخش";
                icon = Icons.PLAY;
            } else if (item.active) {
                label = "توقف";
                icon = Icons.PAUSE;
            } else {
                label = "ادامه دانلود";
                icon = Icons.DOWNLOAD;
            }
            actions.addView(Ui.pill(c, label, icon, Ui.K_PRIMARY, 46, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    primary(item);
                }
            }), new LinearLayout.LayoutParams(0, WRAP, 1f));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(46), dp(46));
            dlp.setMarginStart(dp(10));
            actions.addView(Ui.iconBtn(c, Icons.TRASH, Ui.DANGER, Ui.SURFACE2, 46, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDelete(item);
                }
            }), dlp);
        }
    }

    // ---------- داده‌ها ----------

    private void refresh() {
        List<Item> list = new ArrayList<>();
        long totalBytes = 0;
        long now = System.currentTimeMillis();
        File[] fs = Store.dir(this).listFiles();
        if (fs != null) {
            Arrays.sort(fs, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return Long.compare(b.lastModified(), a.lastModified());
                }
            });
            SharedPreferences p = Store.prefs(this);
            for (File f : fs) {
                if (!f.isFile()) continue;
                Item it = new Item();
                it.f = f;
                String n = f.getName();
                if (n.endsWith(".part")) {
                    it.part = true;
                    it.name = n.substring(0, n.length() - 5);
                    it.total = p.getLong("total_" + it.name, -1);
                    it.url = p.getString("url_" + it.name, null);
                    it.active = DownloadService.active.contains(it.name);
                } else {
                    it.name = n;
                }
                it.size = f.length();
                if (it.active) {
                    long[] prev = speedMap.get(it.name);
                    if (prev != null && now > prev[1] && it.size >= prev[0]) {
                        it.speed = (it.size - prev[0]) * 1000 / (now - prev[1]);
                    }
                    speedMap.put(it.name, new long[]{it.size, now});
                } else {
                    speedMap.remove(it.name);
                }
                totalBytes += it.size;
                list.add(it);
            }
        }
        StringBuilder sig = new StringBuilder();
        for (Item it : list) {
            sig.append(it.name).append('|').append(it.part).append('|').append(it.active).append('|')
                    .append(it.size).append('|').append(it.total).append('|').append(it.speed).append(';');
        }
        items.clear();
        items.addAll(list);
        subtitle.setText(list.size() + " فایل  •  " + Formatter.formatFileSize(this, totalBytes));
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        String s = sig.toString();
        if (!s.equals(lastSig)) {
            lastSig = s;
            adapter.notifyDataSetChanged();
        }
    }

    // ---------- عملیات ----------

    private void primary(Item it) {
        if (!it.part) {
            startActivity(new Intent(this, PlayerActivity.class)
                    .putExtra("src", Uri.fromFile(it.f).toString()));
        } else if (it.active) {
            DownloadService.cancelled.add(it.name);
        } else if (it.url != null) {
            Store.startDownload(this, it.url, it.name);
        } else {
            Toast.makeText(this, "لینک دانلود ذخیره نشده؛ دوباره از سایت بزن", Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDelete(final Item it) {
        LinearLayout card = Ui.card(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = Ui.text(this, "حذف شود؟", 18, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));
        TextView nm = Ui.text(this, it.name, 12, Ui.MUTED, false);
        nm.setGravity(Gravity.CENTER);
        nm.setMaxLines(2);
        nm.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        nm.setPadding(0, dp(6), 0, dp(16));
        card.addView(nm, new LinearLayout.LayoutParams(MATCH, WRAP));

        final Dialog d = Ui.sheet(this, card);
        card.addView(Ui.pill(this, "حذف", Icons.TRASH, Ui.K_DANGER, 52, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                if (it.active) {
                    DownloadService.toDelete.add(it.name);
                    DownloadService.cancelled.add(it.name);
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    it.f.delete();
                    Store.prefs(DownloadsActivity.this).edit()
                            .remove("total_" + it.name).remove("url_" + it.name).apply();
                }
                refresh();
            }
        }), Ui.fill(this, 10));
        card.addView(Ui.pill(this, "انصراف", Icons.BACK, Ui.K_SECONDARY, 52, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        }), Ui.fill(this, 4));
        d.show();
    }

    private void askLink() {
        LinearLayout card = Ui.card(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = Ui.text(this, "دانلود با لینک مستقیم", 18, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(14));
        card.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));

        final EditText et = new EditText(this);
        et.setHint("https://...");
        et.setHintTextColor(0x88B9ABDD);
        et.setTextColor(Ui.TEXT);
        et.setSingleLine(true);
        et.setTextDirection(View.TEXT_DIRECTION_LTR);
        et.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        et.setBackground(Ui.shape(this, Ui.SURFACE2, 16));
        et.setPadding(dp(16), dp(14), dp(16), dp(14));
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                    && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence cs = cm.getPrimaryClip().getItemAt(0).getText();
                if (cs != null && cs.toString().trim().startsWith("http")) et.setText(cs.toString().trim());
            }
        } catch (Exception ignored) {
        }
        card.addView(et, Ui.fill(this, 14));

        final Dialog d = Ui.sheet(this, card);
        card.addView(Ui.pill(this, "شروع دانلود", Icons.DOWNLOAD, Ui.K_PRIMARY, 52, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String u = et.getText().toString().trim();
                if (!u.startsWith("http")) {
                    Toast.makeText(DownloadsActivity.this, "لینک معتبر نیست", Toast.LENGTH_SHORT).show();
                    return;
                }
                d.dismiss();
                Store.startDownload(DownloadsActivity.this, u, Store.nameFromUrl(u));
            }
        }), Ui.fill(this, 4));
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
    }
}
