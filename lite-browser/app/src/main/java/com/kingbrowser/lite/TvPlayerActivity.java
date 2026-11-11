package com.kingbrowser.lite;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.HashMap;
import java.util.Map;

public class TvPlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView playerView;
    private TextView errorLabel;
    private String streamUrl;
    private String channelName;
    private final HashMap<String, String> streamHeaders = new HashMap<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        CrashLog.install(getApplicationContext());
        CrashLog.write(getApplicationContext(), "TV player onCreate");
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().getDecorView().setBackgroundColor(Color.BLACK);
            streamUrl = getIntent().getStringExtra("stream_url");
            channelName = getIntent().getStringExtra("channel_name");
            Bundle suppliedHeaders = getIntent().getBundleExtra("stream_headers");
            if (suppliedHeaders != null) {
                for (String key : suppliedHeaders.keySet()) {
                    String value = suppliedHeaders.getString(key);
                    if (value != null && value.length() <= 2048 &&
                            ("User-Agent".equalsIgnoreCase(key) || "Referer".equalsIgnoreCase(key) ||
                             "Origin".equalsIgnoreCase(key))) {
                        streamHeaders.put(key, value);
                    }
                }
            }

            FrameLayout root = new FrameLayout(this);
            root.setBackgroundColor(Color.BLACK);
            playerView = new PlayerView(this);
            playerView.setUseController(true);
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
            playerView.setControllerAutoShow(true);
            root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

            errorLabel = new TextView(this);
            errorLabel.setTextColor(Color.WHITE);
            errorLabel.setTextSize(16);
            errorLabel.setGravity(android.view.Gravity.CENTER);
            errorLabel.setPadding(32, 24, 32, 24);
            errorLabel.setBackgroundColor(0xCC101322);
            errorLabel.setVisibility(View.GONE);
            root.addView(errorLabel, new FrameLayout.LayoutParams(-1, -2, android.view.Gravity.CENTER));

            TextView title = new TextView(this);
            title.setText(channelName == null ? "KING LIVE TV" : channelName);
            title.setTextColor(Color.WHITE);
            title.setTextSize(14);
            title.setPadding(18, 12, 18, 12);
            title.setBackgroundColor(0x99000000);
            root.addView(title, new FrameLayout.LayoutParams(-1, -2,
                    android.view.Gravity.TOP | android.view.Gravity.START));

            setContentView(root);
            enterImmersiveMode();
            if (streamUrl == null || !(streamUrl.startsWith("https://") || streamUrl.startsWith("http://"))) {
                showError("Invalid channel stream URL.");
                return;
            }
            startStream();
        } catch (Throwable error) {
            CrashLog.write(getApplicationContext(), "TV player screen initialization failed", error);
            Toast.makeText(this, "TV player could not initialize. Diagnostic log saved.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void startStream() {
        releasePlayer();
        if (errorLabel != null) errorLabel.setVisibility(View.GONE);
        try {
            HashMap<String, String> requestHeaders = new HashMap<>(streamHeaders);
            requestHeaders.putIfAbsent("User-Agent", "KING-BROWSER-Lite/1.2 (Android)");
            DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent(requestHeaders.remove("User-Agent"))
                    .setDefaultRequestProperties(requestHeaders)
                    .setConnectTimeoutMs(12000)
                    .setReadTimeoutMs(15000)
                    .setAllowCrossProtocolRedirects(true);
            player = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(httpFactory))
                    .build();
            playerView.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override public void onPlayerError(PlaybackException error) {
                    CrashLog.write(getApplicationContext(), "TV playback error: " + error.getErrorCodeName(), error);
                    showError("This channel could not play (" + error.getErrorCodeName() + ").\nTry another channel.");
                }
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_BUFFERING && errorLabel != null) errorLabel.setVisibility(View.GONE);
                }
            });
            MediaItem item = new MediaItem.Builder().setUri(Uri.parse(streamUrl)).build();
            player.setMediaItem(item);
            player.prepare();
            player.setPlayWhenReady(true);
        } catch (Exception error) {
            CrashLog.write(getApplicationContext(), "TV player failed to initialize stream", error);
            showError("Unable to start this stream. Try another channel.");
        }
    }

    private void showError(String message) {
        if (errorLabel != null) {
            errorLabel.setText(message + "\n\nTap to retry");
            errorLabel.setVisibility(View.VISIBLE);
            errorLabel.setOnClickListener(v -> startStream());
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void releasePlayer() {
        if (player != null) {
            if (playerView != null) playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        enterImmersiveMode();
    }

    @Override protected void onPause() {
        if (player != null) player.pause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (player != null && player.getPlaybackState() != Player.STATE_ENDED) {
            player.play();
        }
    }

    @Override protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}
