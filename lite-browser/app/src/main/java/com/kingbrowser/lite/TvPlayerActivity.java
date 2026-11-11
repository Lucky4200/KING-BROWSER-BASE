package com.kingbrowser.lite;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

public class TvPlayerActivity extends Activity {
    private VideoView video;
    private TextView label;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        String url = getIntent().getStringExtra("stream_url");
        String name = getIntent().getStringExtra("channel_name");
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        video = new VideoView(this);
        frame.addView(video, new FrameLayout.LayoutParams(-1, -1));

        label = new TextView(this);
        label.setText(name == null ? "Live TV" : name);
        label.setTextColor(Color.WHITE);
        label.setTextSize(15);
        label.setPadding(18, 12, 18, 12);
        label.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(-2, -2,
                android.view.Gravity.TOP | android.view.Gravity.START);
        frame.addView(label, labelParams);
        setContentView(frame);
        enterImmersiveMode();

        MediaController controls = new MediaController(this);
        controls.setAnchorView(video);
        video.setMediaController(controls);
        if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
            video.setVideoPath(url);
            video.setOnPreparedListener(mp -> {
                mp.setOnVideoSizeChangedListener((mediaPlayer, width, height) -> {
                    video.requestLayout();
                    enterImmersiveMode();
                });
                video.start();
            });
            video.setOnErrorListener((mp, what, extra) -> {
                android.widget.Toast.makeText(this,
                        "This stream is offline or unsupported. Try another channel.",
                        android.widget.Toast.LENGTH_LONG).show();
                return true;
            });
        } else {
            finish();
        }
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        enterImmersiveMode();
        if (video != null) video.requestLayout();
    }

    @Override protected void onPause() {
        if (video != null && video.isPlaying()) video.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (video != null) video.stopPlayback();
        super.onDestroy();
    }
}
