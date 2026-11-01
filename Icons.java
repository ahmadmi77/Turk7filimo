package ir.turk7filimo.app;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.PathParser;

/** آیکن‌های برداری (Material) که با کد کشیده می‌شوند، بدون فایل جداگانه */
final class Icons {
    static final int HOME = 0, FILM = 1, DOWNLOAD = 2, REFRESH = 3, PLAY = 4,
            PAUSE = 5, TRASH = 6, ADD = 7, BACK = 8;

    private static final String[] PATHS = {
            "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z",
            "M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z",
            "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z",
            "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z",
            "M8 5v14l11-7z",
            "M6 19h4V5H6v14zm8-14v14h4V5h-4z",
            "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z",
            "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z",
            "M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z"
    };

    private Icons() {}

    static Drawable get(int type, int color) {
        return new PathDrawable(PATHS[type], color);
    }

    static final class PathDrawable extends Drawable {
        private final Path path;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PathDrawable(String d, int color) {
            Path p = null;
            try {
                p = PathParser.createPathFromPathData(d);
            } catch (Throwable ignored) {
            }
            path = p != null ? p : new Path();
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            float s = Math.min(b.width(), b.height()) / 24f;
            int save = canvas.save();
            canvas.translate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f);
            canvas.scale(s, s);
            canvas.drawPath(path, paint);
            canvas.restoreToCount(save);
        }

        @Override
        public void setAlpha(int a) {
            paint.setAlpha(a);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
