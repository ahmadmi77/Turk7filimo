package ir.turk7filimo.app;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ابزار زیرنویس: خواندن فایل (UTF-8 یا ویندوز-۱۲۵۶) و جابه‌جا کردن زمان */
final class SubUtil {
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})");

    private SubUtil() {}

    static File subsDir(Context c) {
        File d = new File(c.getFilesDir(), "subs");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) bo.write(buf, 0, r);
        in.close();
        return bo.toByteArray();
    }

    static String decode(byte[] b) throws Exception {
        int off = 0;
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) off = 3;
        try {
            return Charset.forName("UTF-8").newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b, off, b.length - off)).toString();
        } catch (CharacterCodingException e) {
            return new String(b, off, b.length - off, "windows-1256");
        }
    }

    /** فایل زیرنویس انتخاب‌شده را به UTF-8 تبدیل و داخل اپ ذخیره می‌کند */
    static File importSub(Context c, Uri uri, String displayName) throws Exception {
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("باز نشد");
        String text = decode(readAll(in)).replace("\r\n", "\n").replace('\r', '\n');
        boolean vtt = displayName != null && displayName.toLowerCase(Locale.ROOT).endsWith(".vtt");
        File out = new File(subsDir(c), "s" + System.currentTimeMillis() + (vtt ? ".vtt" : ".srt"));
        FileOutputStream fo = new FileOutputStream(out);
        fo.write(text.getBytes("UTF-8"));
        fo.close();
        return out;
    }

    /** یک کپی از زیرنویس می‌سازد که همه‌ی زمان‌هایش به اندازه‌ی offsetMs جابه‌جا شده */
    static File shift(Context c, File src, long offsetMs) throws Exception {
        FileInputStream in = new FileInputStream(src);
        String text = new String(readAll(in), "UTF-8");
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (line.contains("-->")) {
                Matcher m = TIME.matcher(line);
                StringBuffer lb = new StringBuffer();
                while (m.find()) {
                    long ms = (Long.parseLong(m.group(1)) * 3600 + Long.parseLong(m.group(2)) * 60
                            + Long.parseLong(m.group(3))) * 1000;
                    String frac = (m.group(4) + "00").substring(0, 3);
                    ms += Long.parseLong(frac) + offsetMs;
                    if (ms < 0) ms = 0;
                    long h = ms / 3600000, mi = (ms / 60000) % 60, s = (ms / 1000) % 60, f = ms % 1000;
                    m.appendReplacement(lb, String.format(Locale.US, "%02d:%02d:%02d,%03d", h, mi, s, f));
                }
                m.appendTail(lb);
                line = lb.toString();
            }
            sb.append(line).append('\n');
        }
        File out = new File(c.getCacheDir(), "shifted_" + Math.abs(offsetMs) + (offsetMs < 0 ? "n" : "p") + "_" + src.getName());
        FileOutputStream fo = new FileOutputStream(out);
        fo.write(sb.toString().getBytes("UTF-8"));
        fo.close();
        return out;
    }
}
