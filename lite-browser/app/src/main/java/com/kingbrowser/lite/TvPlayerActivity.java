package com.kingbrowser.lite;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

public class TvPlayerActivity extends Activity {
    private VideoView video;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);
        String url = getIntent().getStringExtra("stream_url");
        String name = getIntent().getStringExtra("channel_name");
        FrameLayout frame = new FrameLayout(this);
        video = new VideoView(this);
        frame.addView(video, new FrameLayout.LayoutParams(-1, -1));

        TextView label = new TextView(this);
        label.setText(name == null ? "Live TV" : name);
        label.setTextColor(Color.WHITE);
        label.setTextSize(15);
        label.setPadding(18, 12, 18, 12);
        label.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        frame.addView(label, labelParams);
        setContentView(frame);

        MediaController controls = new MediaController(this);
        controls.setAnchorView(video);
        video.setMediaController(controls);
        if (url != null && url.startsWith("https://")) {
            video.setVideoPath(url);
            video.setOnErrorListener((mp, what, extra) -> {
                android.widget.Toast.makeText(this, "This stream is offline or unsupported. Please choose another channel.", android.widget.Toast.LENGTH_LONG).show();
                return true;
            });
            video.start();
        } else {
            finish();
        }
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