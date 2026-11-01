package ir.turk7filimo.app;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.HashMap;
import java.util.Map;

public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private String src;

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

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        PlayerView pv = new PlayerView(this);
        root.addView(pv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        // لوگوی کم‌رنگ گوشه‌ی بالا
        ImageView mark = new ImageView(this);
        mark.setImageDrawable(getDrawable(R.drawable.logo));
        mark.setAlpha(0.8f);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                Store.dp(this, 38), Store.dp(this, 38), Gravity.TOP | Gravity.END);
        mlp.setMargins(Store.dp(this, 16), Store.dp(this, 14), Store.dp(this, 16), 0);
        root.addView(mark, mlp);

        setContentView(root);
        immersive();

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
            public void onPlayerError(PlaybackException e) {
                Toast.makeText(PlayerActivity.this, "پخش ممکن نشد: " + e.getErrorCodeName(), Toast.LENGTH_LONG).show();
            }
        });

        long start = Store.prefs(this).getLong("pos_" + src, 0);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(src)), start);
        player.prepare();
        player.setPlayWhenReady(true);
    }

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
    protected void onPause() {
        if (player != null && src != null) {
            player.pause();
            long d = player.getDuration();
            long p = player.getCurrentPosition();
            if (d > 0 && p > 5000 && p < d - 15000) {
                Store.prefs(this).edit().putLong("pos_" + src, p).apply();
            } else {
                Store.prefs(this).edit().remove("pos_" + src).apply();
            }
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
