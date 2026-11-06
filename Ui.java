package ir.turk7filimo.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** تم و ابزارهای ساخت ظاهر. رنگ‌ها را اگر خواستی فقط از همین‌جا عوض کن. */
final class Ui {
    static final int BG = 0xFF0E0A1C;        // پس‌زمینه‌ی اصلی
    static final int SURFACE = 0xFF1A1330;   // کارت‌ها و نوار پایین
    static final int SURFACE2 = 0xFF261B4A;  // دکمه‌های ثانویه
    static final int P1 = 0xFFA259FF;        // بنفش روشن
    static final int P2 = 0xFF7B1FFF;        // بنفش تیره
    static final int TEXT = 0xFFFFFFFF;
    static final int MUTED = 0xFFB9ABDD;
    static final int DANGER = 0xFFFF4D6D;
    static final int OK = 0xFF3DDC97;
    static final int BADGE = 0xFF120726;     // دایره‌ی پشت لوگو داخل دکمه‌ها

    static final int K_PRIMARY = 0, K_SECONDARY = 1, K_DANGER = 2;
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private Ui() {}

    static int dp(Context c, int v) {
        return Store.dp(c, v);
    }

    static GradientDrawable shape(Context c, int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable grad(Context c, int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{P1, P2});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    static Drawable ripple(Context c, Drawable bg, int radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, shape(c, Color.WHITE, radiusDp));
    }

    static TextView text(Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    static ImageView logo(Context c, int sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(c.getDrawable(R.drawable.logo));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    static LinearLayout.LayoutParams fill(Context c, int bottomDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        p.bottomMargin = dp(c, bottomDp);
        return p;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(shape(c, SURFACE, 26));
        l.setPadding(dp(c, 18), dp(c, 20), dp(c, 18), dp(c, 16));
        return l;
    }

    /** دکمه‌ی گرد و گرادیانی با لوگو داخلش (برای پخش، دانلود و ...) */
    static View pill(Context c, String label, int icon, int kind, int heightDp, View.OnClickListener l) {
        int r = heightDp / 2;
        Drawable bg = kind == K_PRIMARY ? grad(c, r) : shape(c, kind == K_DANGER ? DANGER : SURFACE2, r);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(c, heightDp));
        row.setPadding(dp(c, 6), dp(c, 6), dp(c, 6), dp(c, 6));
        row.setBackground(ripple(c, bg, r));
        row.setClickable(true);
        row.setOnClickListener(l);

        int badgeDp = heightDp - 12;
        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(circle(BADGE));
        int lg = dp(c, badgeDp * 2 / 3);
        ImageView logo = new ImageView(c);
        logo.setImageDrawable(c.getDrawable(R.drawable.logo));
        badge.addView(logo, new FrameLayout.LayoutParams(lg, lg, Gravity.CENTER));
        row.addView(badge, new LinearLayout.LayoutParams(dp(c, badgeDp), dp(c, badgeDp)));

        TextView t = text(c, label, heightDp >= 48 ? 15 : 13, TEXT, true);
        t.setGravity(Gravity.CENTER);
        row.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));

        ImageView ic = new ImageView(c);
        ic.setImageDrawable(Icons.get(icon, TEXT));
        int is = dp(c, heightDp >= 48 ? 22 : 18);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(is, is);
        ilp.setMarginEnd(dp(c, 8));
        row.addView(ic, ilp);
        return row;
    }

    /** دکمه‌ی گرد فقط با آیکن */
    static View iconBtn(Context c, int icon, int iconColor, int bgColor, int sizeDp, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), circle(bgColor), circle(Color.WHITE)));
        f.setClickable(true);
        f.setOnClickListener(l);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(Icons.get(icon, iconColor));
        int in = dp(c, sizeDp * 5 / 11);
        f.addView(iv, new FrameLayout.LayoutParams(in, in, Gravity.CENTER));
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return f;
    }

    /** پنجره‌ی پایین‌صفحه با گوشه‌های گرد */
    static Dialog sheet(Activity a, View card) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout wrap = new FrameLayout(a);
        wrap.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        wrap.setPadding(dp(a, 12), dp(a, 12), dp(a, 12), dp(a, 12));
        wrap.addView(card, new FrameLayout.LayoutParams(MATCH, WRAP));
        d.setContentView(wrap);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(MATCH, WRAP);
            w.setGravity(Gravity.BOTTOM);
            w.setDimAmount(0.65f);
        }
        return d;
    }
    interface Pick {
        void on(int index);
    }

    /** پنجره‌ی انتخاب از لیست */
    static Dialog choose(Activity a, String title, java.util.List<String> labels, int selected, final Pick cb) {
        LinearLayout card = card(a);
        TextView t = text(a, title, 18, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(a, 12));
        card.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
        final Dialog d = sheet(a, card);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < labels.size(); i++) {
            final int idx = i;
            TextView row = text(a, (i == selected ? "✓  " : "") + labels.get(i), 16, i == selected ? TEXT : MUTED, i == selected);
            row.setPadding(dp(a, 18), dp(a, 14), dp(a, 18), dp(a, 14));
            row.setBackground(i == selected ? grad(a, 18) : shape(a, SURFACE2, 18));
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    d.dismiss();
                    cb.on(idx);
                }
            });
            list.addView(row, fill(a, 8));
        }
        ScrollView sv = new ScrollView(a);
        sv.addView(list, new FrameLayout.LayoutParams(MATCH, WRAP));
        int h = Math.min(labels.size() * 62 + 8, 380);
        card.addView(sv, new LinearLayout.LayoutParams(MATCH, dp(a, h)));
        d.show();
        return d;
    }

    /** پنجره‌ی تأیید کار خطرناک (مثل حذف) */
    static void confirm(Activity a, String title, String sub, String yes, final Runnable ok) {
        LinearLayout card = card(a);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = text(a, title, 18, TEXT, true);
        t.setGravity(Gravity.CENTER);
        card.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
        TextView s = text(a, sub, 13, MUTED, false);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, dp(a, 6), 0, dp(a, 16));
        card.addView(s, new LinearLayout.LayoutParams(MATCH, WRAP));
        final Dialog d = sheet(a, card);
        card.addView(pill(a, yes, Icons.TRASH, K_DANGER, 52, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                ok.run();
            }
        }), fill(a, 10));
        card.addView(pill(a, "انصراف", Icons.BACK, K_SECONDARY, 52, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        }), fill(a, 4));
        d.show();
    }

    /** برچسب کوچک گرد برای نمایش مقدار یک تنظیم */
    static TextView valuePill(Context c, String s, boolean on) {
        TextView t = text(c, s, 13, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7));
        t.setBackground(on ? grad(c, 16) : shape(c, 0xFF3A2D6B, 16));
        return t;
    }
}
