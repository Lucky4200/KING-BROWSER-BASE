package com.kingbrowser.lite;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

/**
 * Dedicated YouTube surface. GeckoView/uBlock is preferred; Android System
 * WebView is kept as a safe fallback if Gecko or the bundled extension fails.
 */
public class GeckoYoutubeActivity extends Activity {
    public static final String EXTRA_URL = "com.kingbrowser.lite.YOUTUBE_URL";
    private static final String UBlockResource = "resource://android/assets/ublock/";
    private static final String UBlockId = "uBlock0@raymondhill.net";

    private static GeckoRuntime runtime;
    private GeckoSession session;
    private GeckoView geckoView;
    private WebView fallbackWebView;
    private FrameLayout root;
    private String targetUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashLog.install(getApplicationContext());
        CrashLog.write(getApplicationContext(), "YouTube activity onCreate");

        targetUrl = getIntent().getStringExtra(EXTRA_URL);
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            targetUrl = "https://m.youtube.com/";
        }

        root = new FrameLayout(this);
        geckoView = new GeckoView(this);
        root.addView(geckoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        try {
            if (runtime == null) {
                runtime = GeckoRuntime.create(getApplicationContext());
            }
            installExtensionThenOpen();
        } catch (Throwable error) {
            CrashLog.write(getApplicationContext(), "GeckoView runtime initialization failed; using Android WebView", error);
            showWebViewFallback(targetUrl);
        }
    }

    private void installExtensionThenOpen() {
        try {
            session = new GeckoSession();
            runtime.getWebExtensionController()
                    .ensureBuiltIn(UBlockResource, UBlockId)
                    .accept(extension -> runOnUiThread(() -> openGeckoPage()),
                            error -> runOnUiThread(() -> {
                                CrashLog.write(getApplicationContext(),
                                        "uBlock extension setup failed; opening YouTube without extension", error);
                                openGeckoPage();
                            }));
        } catch (Throwable error) {
            CrashLog.write(getApplicationContext(),
                    "GeckoView extension setup threw; trying YouTube without extension", error);
            openGeckoPage();
        }
    }

    private void openGeckoPage() {
        if (isFinishing() || isDestroyed()) return;
        try {
            if (session == null) session = new GeckoSession();
            session.open(runtime);
            geckoView.setSession(session);
            session.loadUri(targetUrl);
            CrashLog.write(getApplicationContext(), "YouTube loaded in GeckoView");
        } catch (Throwable error) {
            CrashLog.write(getApplicationContext(), "GeckoView page open failed; using Android WebView", error);
            showWebViewFallback(targetUrl);
        }
    }

    private void showWebViewFallback(String url) {
        if (isFinishing() || isDestroyed()) return;
        try {
            if (geckoView != null) {
                ViewParent parent = geckoView.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(geckoView);
                }
                geckoView.releaseSession();
                geckoView = null;
            }

            fallbackWebView = new WebView(this);
            WebSettings settings = fallbackWebView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setLoadsImagesAutomatically(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setSupportMultipleWindows(false);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            String agent = settings.getUserAgentString();
            if (agent != null) {
                settings.setUserAgentString(agent.replace("; wv", "").replace("Version/4.0 ", ""));
            }
            fallbackWebView.setWebViewClient(new WebViewClient());
            fallbackWebView.setWebChromeClient(new WebChromeClient());
            root.addView(fallbackWebView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            fallbackWebView.loadUrl(url);
            CrashLog.write(getApplicationContext(), "YouTube opened in Android System WebView fallback");
            Toast.makeText(this, "YouTube opened in compatibility mode.", Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            CrashLog.write(getApplicationContext(), "YouTube WebView fallback also failed", error);
            Toast.makeText(this, "YouTube could not open. Diagnostic log saved.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (fallbackWebView != null && fallbackWebView.canGoBack()) {
            fallbackWebView.goBack();
        } else {
            finish();
        }
    }

    @Override
    protected void onPause() {
        if (fallbackWebView != null) fallbackWebView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (fallbackWebView != null) fallbackWebView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (fallbackWebView != null) {
            fallbackWebView.stopLoading();
            fallbackWebView.destroy();
            fallbackWebView = null;
        }
        if (geckoView != null) {
            geckoView.releaseSession();
            geckoView = null;
        }
        if (session != null) {
            try {
                session.close();
            } catch (Throwable error) {
                CrashLog.write(getApplicationContext(), "GeckoSession close failed", error);
            }
            session = null;
        }
        super.onDestroy();
    }
}
