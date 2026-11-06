package ir.turk7filimo.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** کتابخانه: ادامه‌ی تماشا، تاریخچه و لیست تماشای بعدی (جدا برای هر پروفایل) */
public class LibraryActivity extends Activity {
    private static final int MATCH = Ui.MATCH, WRAP = Ui.WRAP;
    private int tab = 0;
    private LinearLayout list;
    private TextView profChip, tabA, tabB;
    private String pageTitle, pageUrl;

    private int dp(int v) {
        return Store.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        pageTitle = getIntent().getStringExtra("page_title");
        pageUrl = getIntent().getStringExtra("page_url");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Ui.BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(12), dp(14), dp(10));
        header.addView(Ui.iconBtn(this, Icons.BACK, Ui.TEXT, Ui.SURFACE2, 44, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }));
        TextView t = Ui.text(this, "کتابخانه", 18, Ui.TEXT, true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, WRAP, 1f);
        tl.setMarginStart(dp(12));
        header.addView(t, tl);
        profChip = Ui.valuePill(this, Data.curName(this), true);
        profChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingsUi.profiles(LibraryActivity.this, new Runnable() {
                    @Override
                    public void run() {
                        render();
                    }
                });
            }
        });
        header.addView(profChip, new LinearLayout.LayoutParams(WRAP, WRAP));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(dp(44), dp(44));
        gl.setMarginStart(dp(8));
        header.addView(Ui.iconBtn(this, Icons.SETTINGS, Ui.TEXT, Ui.SURFACE2, 44, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingsUi.show(LibraryActivity.this, new Runnable() {
                    @Override
                    public void run() {
                        render();
                    }
                }, null);
            }
        }), gl);
        root.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(dp(14), 0, dp(14), dp(8));
        tabA = tabView("ادامه و تاریخچه", 0);
        tabB = tabView("تماشای بعدی", 1);
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, WRAP, 1f);
        a.setMarginEnd(dp(8));
        tabs.addView(tabA, a);
        tabs.addView(tabB, new LinearLayout.LayoutParams(0, WRAP, 1f));
        root.addView(tabs, new LinearLayout.LayoutParams(MATCH, WRAP));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(4), dp(14), dp(20));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list, new FrameLayout.LayoutParams(MATCH, WRAP));
        root.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        setContentView(root);
    }

    private TextView tabView(String s, final int i) {
        TextView v = Ui.text(this, s, 14, Ui.TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, dp(11), 0, dp(11));
        v.setClickable(true);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                tab = i;
                render();
            }
        });
        return v;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Night.apply(this);
        render();
    }

    private void render() {
        profChip.setText(Data.curName(this));
        tabA.setBackground(tab == 0 ? Ui.grad(this, 16) : Ui.shape(this, Ui.SURFACE2, 16));
        tabB.setBackground(tab == 1 ? Ui.grad(this, 16) : Ui.shape(this, Ui.SURFACE2, 16));
        list.removeAllViews();
        if (tab == 0) renderHistory();
        else renderWatch();
    }

    private void emptyMsg(String s) {
        TextView e = Ui.text(this, s, 14, Ui.MUTED, false);
        e.setGravity(Gravity.CENTER);
        e.setPadding(dp(20), dp(50), dp(20), 0);
        list.addView(e, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.shape(this, Ui.SURFACE, 20));
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        return c;
    }

    private void renderHistory() {
        final List<Data.Entry> h = Data.history(this);
        if (h.isEmpty()) {
            emptyMsg("هنوز چیزی تماشا نکرده‌ای.\nفیلمی که ببینی اینجا ثبت می‌شود و دفعه‌ی بعد از همان ثانیه ادامه پیدا می‌کند.");
            return;
        }
        for (final Data.Entry e : h) {
            LinearLayout c = card();
            TextView n = Ui.text(this, e.title, 14, Ui.TEXT, true);
            n.setMaxLines(2);
            n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            c.addView(n, new LinearLayout.LayoutParams(MATCH, WRAP));
            TextView st = Ui.text(this, e.done() ? "تماشا شد ✓" : "ادامه از " + Store.fmtTime(e.pos)
                    + (e.dur > 0 ? " از " + Store.fmtTime(e.dur) : ""), 12, e.done() ? Ui.OK : Ui.P1, false);
            st.setPadding(0, dp(4), 0, dp(8));
            c.addView(st, new LinearLayout.LayoutParams(MATCH, WRAP));
            DownloadsActivity.ProgressLine pl = new DownloadsActivity.ProgressLine(this);
            pl.set(e.dur > 0 ? (float) e.pos / e.dur : 0f);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(MATCH, dp(5));
            plp.bottomMargin = dp(10);
            c.addView(pl, plp);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(Ui.pill(this, e.done() ? "پخش دوباره" : "ادامه", Icons.PLAY, Ui.K_PRIMARY, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(LibraryActivity.this, PlayerActivity.class)
                            .putExtra("src", e.key).putExtra("title", e.title));
                }
            }), new LinearLayout.LayoutParams(0, WRAP, 1f));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(44), dp(44));
            dl.setMarginStart(dp(10));
            row.addView(Ui.iconBtn(this, Icons.TRASH, Ui.DANGER, Ui.SURFACE2, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Data.removeHistory(LibraryActivity.this, e.key);
                    render();
                }
            }), dl);
            c.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
            list.addView(c, Ui.fill(this, 10));
        }
        TextView clr = Ui.text(this, "پاک کردن کل تاریخچه", 13, Ui.DANGER, false);
        clr.setGravity(Gravity.CENTER);
        clr.setPadding(0, dp(10), 0, dp(10));
        clr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.confirm(LibraryActivity.this, "تاریخچه پاک شود؟", "همه‌ی مکان‌های ذخیره‌شده‌ی پخش پاک می‌شود", "پاک کن", new Runnable() {
                    @Override
                    public void run() {
                        Data.clearHistory(LibraryActivity.this);
                        render();
                    }
                });
            }
        });
        list.addView(clr, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private void renderWatch() {
        if (pageUrl != null && pageUrl.startsWith("http") && !Data.inWatch(this, pageUrl)
                && !pageUrl.equals(Store.HOME)) {
            final String pt = pageTitle, pu = pageUrl;
            list.addView(Ui.pill(this, "افزودن صفحه‌ی فعلی به لیست", Icons.BOOKMARK, Ui.K_PRIMARY, 48, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Data.addWatch(LibraryActivity.this, pt, pu);
                    render();
                }
            }), Ui.fill(this, 12));
        }
        List<Data.Entry> w = Data.watch(this);
        if (w.isEmpty()) {
            emptyMsg("لیست خالی است.\nداخل سایت صفحه‌ی فیلم را باز کن، «ویدیوهای صفحه» را بزن و «افزودن به بعداً می‌بینم» را انتخاب کن.");
            return;
        }
        for (final Data.Entry e : w) {
            LinearLayout c = card();
            TextView n = Ui.text(this, e.title, 14, Ui.TEXT, true);
            n.setMaxLines(2);
            n.setEllipsize(TextUtils.TruncateAt.END);
            c.addView(n, new LinearLayout.LayoutParams(MATCH, WRAP));
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(Ui.pill(this, "باز کردن در سایت", Icons.FILM, Ui.K_PRIMARY, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent it = new Intent(LibraryActivity.this, MainActivity.class)
                            .putExtra("open_url", e.url)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(it);
                    finish();
                }
            }), new LinearLayout.LayoutParams(0, WRAP, 1f));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(44), dp(44));
            dl.setMarginStart(dp(10));
            row.addView(Ui.iconBtn(this, Icons.TRASH, Ui.DANGER, Ui.SURFACE2, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Data.removeWatch(LibraryActivity.this, e.url);
                    Toast.makeText(LibraryActivity.this, "حذف شد", Toast.LENGTH_SHORT).show();
                    render();
                }
            }), dl);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(MATCH, WRAP);
            rl.topMargin = dp(10);
            c.addView(row, rl);
            list.addView(c, Ui.fill(this, 10));
        }
    }
}
