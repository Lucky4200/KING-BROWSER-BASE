package com.kingbrowser.lite;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private WebView webView;
    private EditText addressBar;
    private ProgressBar progressBar;

    // Lightweight built-in domain blocker. This is a basic starter list, not a full EasyList engine.
    private static final Set<String> BLOCKED_HOSTS = new HashSet<>(Arrays.asList(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "adnxs.com",
        "adsrvr.org",
        " تبلیغات.ir",
        "taboola.com",
        "outbrain.com",
        "adform.net",
        "adcolony.com",
        "appsflyer.com",
        "branch.io",
        "scorecardresearch.com",
        "zedo.com",
        "popads.net",
        "propellerads.com"
    ));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 18, 24));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setPadding(dp(10), dp(10), dp(10), dp(8));
        toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setHint("Search or enter website");
        addressBar.setHintTextColor(Color.LTGRAY);
        addressBar.setTextColor(Color.WHITE);
        addressBar.setTextSize(14);
        addressBar.setPadding(dp(14), 0, dp(14), 0);
        addressBar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(120, 90, 220)));
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        toolbar.addView(addressBar, new LinearLayout.LayoutParams(0, dp(46), 1f));

        Button go = new Button(this);
        go.setText("GO");
        go.setTextColor(Color.WHITE);
        go.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(105, 75, 205)));
        toolbar.addView(go, new LinearLayout.LayoutParams(dp(64), dp(46)));

        root.addView(toolbar);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(145, 110, 255)));
        root.addView(progressBar, new LinearLayout.LayoutParams(-1, dp(2)));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        webView.setBackgroundColor(Color.WHITE);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (isAdHost(request.getUrl().getHost())) {
                    return new WebResourceResponse(
                        "text/plain", "UTF-8",
                        204, "No Content",
                        java.util.Collections.emptyMap(),
                        new ByteArrayInputStream(new byte[0])
                    );
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                addressBar.setText(url);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                addressBar.setText(url);
                progressBar.setVisibility(View.GONE);
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        go.setOnClickListener(v -> loadAddress());
        addressBar.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadAddress();
                return true;
            }
            return false;
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl("about:blank");
        }
    }

    private boolean isAdHost(String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String blocked : BLOCKED_HOSTS) {
            if (normalized.equals(blocked) || normalized.endsWith("." + blocked)) return true;
        }
        return false;
    }

    private void loadAddress() {
        String value = addressBar.getText().toString().trim();
        if (value.isEmpty()) return;
        String url;
        if (value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            url = value;
        } else if (value.contains(" ") || !value.contains(".")) {
            url = "https://www.google.com/search?q=" + android.net.Uri.encode(value);
        } else {
            url = "https://" + value;
        }
        webView.loadUrl(url);
        addressBar.clearFocus();
        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
