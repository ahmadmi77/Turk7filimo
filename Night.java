package ir.turk7filimo.app;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** حالت شب سینمایی: یک لایه‌ی کم‌نور و گرم روی کل صفحه */
final class Night {
    static final String[] NAMES = {"خاموش", "ملایم", "متوسط", "تاریک"};
    private static final int[] DIM = {0, 26, 64, 105};
    private static final int[] WARM = {0, 22, 36, 46};

    private Night() {}

    static void apply(Activity a) {
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null) return;
        View old = content.findViewWithTag("night_overlay");
        int lv = Data.night(a);
        if (lv <= 0) {
            if (old != null) content.removeView(old);
            return;
        }
        FrameLayout f;
        if (old instanceof FrameLayout) {
            f = (FrameLayout) old;
            f.removeAllViews();
        } else {
            f = new FrameLayout(a);
            f.setTag("night_overlay");
            f.setClickable(false);
            f.setFocusable(false);
            content.addView(f, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        View warm = new View(a);
        warm.setBackgroundColor(Color.argb(WARM[lv], 255, 150, 60));
        View dim = new View(a);
        dim.setBackgroundColor(Color.argb(DIM[lv], 0, 0, 0));
        f.addView(warm, new FrameLayout.LayoutParams(-1, -1));
        f.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        f.bringToFront();
    }
}
