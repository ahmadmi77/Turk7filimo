package ir.turk7filimo.app;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** پنجره‌ی تنظیمات و مدیریت پروفایل‌ها */
final class SettingsUi {
    private static final int MATCH = Ui.MATCH, WRAP = Ui.WRAP;

    private SettingsUi() {}

    private static LinearLayout row(Activity a, int icon, String title, TextView value, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(Ui.ripple(a, Ui.shape(a, Ui.SURFACE2, 18), 18));
        r.setPadding(Store.dp(a, 16), Store.dp(a, 14), Store.dp(a, 14), Store.dp(a, 14));
        r.setClickable(true);
        r.setOnClickListener(l);
        android.widget.ImageView ic = new android.widget.ImageView(a);
        ic.setImageDrawable(Icons.get(icon, Ui.P1));
        r.addView(ic, new LinearLayout.LayoutParams(Store.dp(a, 26), Store.dp(a, 26)));
        TextView t = Ui.text(a, title, 15, Ui.TEXT, true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, WRAP, 1f);
        tl.setMarginStart(Store.dp(a, 14));
        r.addView(t, tl);
        if (value != null) r.addView(value, new LinearLayout.LayoutParams(WRAP, WRAP));
        return r;
    }

    private static void setPill(TextView v, String s, boolean on, Activity a) {
        v.setText(s);
        v.setBackground(on ? Ui.grad(a, 16) : Ui.shape(a, 0xFF3A2D6B, 16));
    }

    static void show(final Activity a, final Runnable onChange, final Runnable onRefresh) {
        LinearLayout card = Ui.card(a);
        TextView title = Ui.text(a, "تنظیمات", 19, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, Store.dp(a, 14));
        card.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));
        final Dialog d = Ui.sheet(a, card);

        // حالت شب سینمایی
        final TextView nightV = Ui.valuePill(a, Night.NAMES[Data.night(a)], Data.night(a) > 0);
        card.addView(row(a, Icons.MOON, "حالت شب سینمایی", nightV, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int lv = (Data.night(a) + 1) % Night.NAMES.length;
                Data.setNight(a, lv);
                setPill(nightV, Night.NAMES[lv], lv > 0, a);
                Night.apply(a);
                if (onChange != null) onChange.run();
            }
        }), Ui.fill(a, 10));

        // فقط Wi-Fi
        final TextView wifiV = Ui.valuePill(a, Data.wifiOnly(a) ? "روشن" : "خاموش", Data.wifiOnly(a));
        card.addView(row(a, Icons.DOWNLOAD, "دانلود فقط با Wi‑Fi", wifiV, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean n = !Data.wifiOnly(a);
                Data.setFlag(a, "wifi", n);
                setPill(wifiV, n ? "روشن" : "خاموش", n, a);
            }
        }), Ui.fill(a, 10));

        // محل ذخیره‌ی دانلودهای جدید
        final boolean pubNow = Data.publicDefault(a) && Publisher.supported();
        final TextView pubV = Ui.valuePill(a, pubNow ? "گالری" : "خصوصی", pubNow);
        card.addView(row(a, Icons.EYE_OFF, "ذخیره‌ی دانلودهای جدید", pubV, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Publisher.supported()) {
                    Toast.makeText(a, "نمایش در گالری از اندروید ۱۰ به بالا کار می‌کند", Toast.LENGTH_LONG).show();
                    return;
                }
                boolean n = !Data.publicDefault(a);
                Data.setFlag(a, "public", n);
                setPill(pubV, n ? "گالری" : "خصوصی", n, a);
            }
        }), Ui.fill(a, 10));

        // پروفایل
        final TextView profV = Ui.valuePill(a, Data.curName(a), true);
        card.addView(row(a, Icons.PERSON, "پروفایل", profV, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                profiles(a, onChange);
            }
        }), Ui.fill(a, 10));

        if (onRefresh != null) {
            card.addView(row(a, Icons.REFRESH, "بارگذاری دوباره‌ی صفحه", null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    d.dismiss();
                    onRefresh.run();
                }
            }), Ui.fill(a, 10));
        }

        TextView ver = Ui.text(a, "turk7filimo  •  نسخه ۳", 12, Ui.MUTED, false);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, Store.dp(a, 6), 0, 0);
        card.addView(ver, new LinearLayout.LayoutParams(MATCH, WRAP));
        d.show();
    }

    /** انتخاب، ساخت و حذف پروفایل */
    static void profiles(final Activity a, final Runnable after) {
        LinearLayout card = Ui.card(a);
        TextView title = Ui.text(a, "پروفایل‌ها", 19, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, Store.dp(a, 6));
        card.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));
        TextView sub = Ui.text(a, "هر پروفایل لیست تماشا و تاریخچه‌ی جدا دارد", 12, Ui.MUTED, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, Store.dp(a, 12));
        card.addView(sub, new LinearLayout.LayoutParams(MATCH, WRAP));
        final Dialog d = Ui.sheet(a, card);

        JSONArray arr = Data.profiles(a);
        final String cur = Data.curId(a);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            final String id = o.optString("id");
            String name = o.optString("name", "—");
            boolean isCur = id.equals(cur);
            TextView r = Ui.text(a, (isCur ? "✓  " : "") + name, 16, isCur ? Ui.TEXT : Ui.MUTED, isCur);
            r.setPadding(Store.dp(a, 18), Store.dp(a, 14), Store.dp(a, 18), Store.dp(a, 14));
            r.setBackground(isCur ? Ui.grad(a, 18) : Ui.shape(a, Ui.SURFACE2, 18));
            r.setClickable(true);
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Data.setCur(a, id);
                    d.dismiss();
                    if (after != null) after.run();
                }
            });
            card.addView(r, Ui.fill(a, 8));
        }

        card.addView(Ui.pill(a, "پروفایل جدید", Icons.ADD, Ui.K_PRIMARY, 50, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                newProfile(a, after);
            }
        }), Ui.fill(a, 8));

        if (arr.length() > 1) {
            final String curName = Data.curName(a);
            TextView del = Ui.text(a, "حذف پروفایل «" + curName + "»", 13, Ui.DANGER, false);
            del.setGravity(Gravity.CENTER);
            del.setPadding(0, Store.dp(a, 10), 0, Store.dp(a, 4));
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    d.dismiss();
                    Ui.confirm(a, "حذف پروفایل «" + curName + "»؟",
                            "لیست تماشا و تاریخچه‌ی این پروفایل پاک می‌شود", "حذف", new Runnable() {
                                @Override
                                public void run() {
                                    Data.deleteProfile(a, cur);
                                    if (after != null) after.run();
                                }
                            });
                }
            });
            card.addView(del, new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        d.show();
    }

    private static void newProfile(final Activity a, final Runnable after) {
        LinearLayout card = Ui.card(a);
        TextView title = Ui.text(a, "اسم پروفایل جدید", 18, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, Store.dp(a, 14));
        card.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));
        final EditText et = new EditText(a);
        et.setHint("مثلاً: مریم");
        et.setHintTextColor(0x88B9ABDD);
        et.setTextColor(Ui.TEXT);
        et.setSingleLine(true);
        et.setBackground(Ui.shape(a, Ui.SURFACE2, 16));
        et.setPadding(Store.dp(a, 16), Store.dp(a, 14), Store.dp(a, 16), Store.dp(a, 14));
        card.addView(et, Ui.fill(a, 14));
        final Dialog d = Ui.sheet(a, card);
        card.addView(Ui.pill(a, "ساختن", Icons.ADD, Ui.K_PRIMARY, 52, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String n = et.getText().toString().trim();
                if (n.isEmpty()) {
                    Toast.makeText(a, "یک اسم بنویس", Toast.LENGTH_SHORT).show();
                    return;
                }
                Data.addProfile(a, n);
                d.dismiss();
                if (after != null) after.run();
            }
        }), Ui.fill(a, 4));
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
    }
}
