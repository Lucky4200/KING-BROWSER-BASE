package com.kingbrowser.lite;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

/**
 * Dedicated GeckoView surface for YouTube.
 * The main browser remains on Android WebView; YouTube is routed here so
 * the Firefox uBlock Origin extension can run in a supported Gecko engine.
 */
public class GeckoYoutubeActivity extends Activity {
    public static final String EXTRA_URL = "com.kingbrowser.lite.YOUTUBE_URL";
    private static final String UBlockResource = "resource://android/assets/ublock/";
    private static final String UBlockId = "uBlock0@raymondhill.net";

    private static GeckoRuntime runtime;
    private GeckoSession session;
    private GeckoView geckoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String targetUrl = getIntent().getStringExtra(EXTRA_URL);
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            targetUrl = "https://m.youtube.com/";
        }

        geckoView = new GeckoView(this);
        FrameLayout root = new FrameLayout(this);
        root.addView(geckoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        try {
            if (runtime == null) {
                runtime = GeckoRuntime.create(getApplicationContext());
            }

            final String initialUrl = targetUrl;
            session = new GeckoSession();

            // Install/load the bundled Firefox extension before opening the page.
            runtime.getWebExtensionController()
                    .ensureBuiltIn(UBlockResource, UBlockId)
                    .accept(extension -> {
                        if (isFinishing() || isDestroyed()) return;
                        session.open(runtime);
                        geckoView.setSession(session);
                        session.loadUri(initialUrl);
                    }, error -> runOnUiThread(() -> {
                        Toast.makeText(this,
                                "uBlock Origin could not initialize: " + error.getMessage(),
                                Toast.LENGTH_LONG).show();
                        finish();
                    }));
        } catch (Throwable error) {
            Toast.makeText(this, "GeckoView failed to start: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onDestroy() {
        if (geckoView != null) {
            geckoView.releaseSession();
            geckoView = null;
        }
        if (session != null) {
            session.close();
            session = null;
        }
        super.onDestroy();
    }
}
