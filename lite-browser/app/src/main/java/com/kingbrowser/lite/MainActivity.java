package com.kingbrowser.lite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import android.util.Base64;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
    private static final String PREFS = "king_browser_settings";
    private static final String PREF_ADBLOCK = "adblock_enabled";
    private static final String PREF_TRACKING = "tracking_enabled";
    private static final String PREF_WHITELIST = "site_whitelist";
    private static final String HOME_URL = "https://kingbrowser.home/";

    private WebView webView;
    private EditText addressBar;
    private ProgressBar progressBar;
    private SharedPreferences preferences;
    private final AtomicInteger blockedRequests = new AtomicInteger(0);

    // Lightweight host-level protection. This is intentionally small to keep the Lite APK fast.
    private static final Set<String> AD_HOSTS = new HashSet<>(Arrays.asList(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "adnxs.com",
        "adsrvr.org",
        "taboola.com",
        "outbrain.com",
        "adform.net",
        "adcolony.com",
        "zedo.com",
        "popads.net",
        "propellerads.com"
    ));

    private static final Set<String> TRACKER_HOSTS = new HashSet<>(Arrays.asList(
        "appsflyer.com",
        "branch.io",
        "scorecardresearch.com",
        " doubleclick.net".trim()
    ));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        AdBlockList.initialize(getApplicationContext());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(9, 13, 28), Color.rgb(25, 20, 48), Color.rgb(8, 20, 35)}
        ));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(10), dp(10), dp(10), dp(9));
        toolbar.setBackground(rounded(Color.argb(145, 27, 32, 54), dp(18)));

        Button home = makeButton("⌂", Color.rgb(65, 76, 112));
        home.setTextSize(22);
        LinearLayout.LayoutParams homeParams = new LinearLayout.LayoutParams(dp(46), dp(44));
        homeParams.rightMargin = dp(7);
        toolbar.addView(home, homeParams);

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setHint("Search or enter website");
        addressBar.setHintTextColor(Color.rgb(177, 183, 205));
        addressBar.setTextColor(Color.WHITE);
        addressBar.setTextSize(14);
        addressBar.setPadding(dp(13), 0, dp(10), 0);
        addressBar.setBackground(rounded(Color.argb(100, 255, 255, 255), dp(14)));
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        toolbar.addView(addressBar, new LinearLayout.LayoutParams(0, dp(46), 1f));

        Button go = makeButton("GO", Color.rgb(116, 91, 220));
        LinearLayout.LayoutParams goParams = new LinearLayout.LayoutParams(dp(58), dp(44));
        goParams.leftMargin = dp(7);
        toolbar.addView(go, goParams);

        Button liveTv = makeButton("TV", Color.rgb(190, 48, 75));
        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(dp(48), dp(44));
        tvParams.leftMargin = dp(6);
        toolbar.addView(liveTv, tvParams);
        liveTv.setOnClickListener(v -> startActivity(new Intent(this, TvChannelsActivity.class)));

        Button menu = makeButton("⋮", Color.argb(105, 255, 255, 255));
        menu.setTextSize(23);
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        menuParams.leftMargin = dp(6);
        toolbar.addView(menu, menuParams);
        root.addView(toolbar);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(157, 128, 255)));
        progressBar.setVisibility(View.GONE);
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
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        webView.setBackgroundColor(Color.TRANSPARENT);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                if (isBlockedHost(host)) {
                    blockedRequests.incrementAndGet();
                    return new WebResourceResponse(
                        "text/plain", "UTF-8", 204, "No Content",
                        Collections.emptyMap(), new ByteArrayInputStream(new byte[0])
                    );
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (!HOME_URL.equals(url)) addressBar.setText(url);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (HOME_URL.equals(url) || url.startsWith("data:text/html")) {
                    addressBar.setText("");
                } else {
                    addressBar.setText(url);
                }
                progressBar.setVisibility(View.GONE);
            }
        });

        // WebView does not open secondary windows, so JavaScript pop-up windows stay blocked.
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                return false;
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        home.setOnClickListener(v -> showHomePage());
        go.setOnClickListener(v -> loadAddress());
        menu.setOnClickListener(v -> showSettings());
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
            showHomePage();
        }
    }

    private boolean isBlockedHost(String host) {
        if (host == null || isWhitelisted(host)) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        if (preferences.getBoolean(PREF_ADBLOCK, true) && (matchesHost(normalized, AD_HOSTS) || AdBlockList.blocks(normalized))) return true;
        return preferences.getBoolean(PREF_TRACKING, true) && matchesHost(normalized, TRACKER_HOSTS);
    }

    private boolean matchesHost(String host, Set<String> domains) {
        for (String domain : domains) {
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        }
        return false;
    }

    private Set<String> getWhitelist() {
        Set<String> saved = preferences.getStringSet(PREF_WHITELIST, Collections.emptySet());
        return new HashSet<>(saved);
    }

    private boolean isWhitelisted(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String allowed : getWhitelist()) {
            if (normalized.equals(allowed) || normalized.endsWith("." + allowed)) return true;
        }
        return false;
    }

    private void showHomePage() {
        boolean adBlockOn = preferences.getBoolean(PREF_ADBLOCK, true);
        String status = adBlockOn ? "AD BLOCK IS ON" : "AD BLOCK IS OFF";
        String backgroundImage = loadImageDataUri("king_browser_background");
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'>" +
            "<style>" +
            "*{box-sizing:border-box}body{margin:0;min-height:100vh;padding:28px 20px 36px;color:#f7f7ff;font-family:Arial,sans-serif;" +
            "background-color:#090d1c;background-image:linear-gradient(145deg,rgba(7,12,27,.78),rgba(18,22,48,.66) 55%,rgba(5,24,38,.78)),url(\'" + backgroundImage + "\');" +
            "background-size:100% 100%;background-repeat:no-repeat;background-position:center center;background-attachment:fixed}" +
            ".brand{font-size:12px;letter-spacing:4px;color:#b9b3ff;font-weight:bold;margin-top:12px}" +
            "h1{font-size:34px;line-height:1.1;margin:12px 0 8px}.sub{color:#b8bfd3;font-size:14px;line-height:1.5}" +
            ".glass{margin-top:26px;padding:20px;border:1px solid rgba(255,255,255,.18);border-radius:22px;" +
            "background:rgba(255,255,255,.09);box-shadow:0 14px 38px rgba(0,0,0,.18)}" +
            ".status{display:inline-block;padding:8px 12px;border-radius:30px;background:rgba(55,205,150,.15);color:#7ff0c2;font-size:11px;letter-spacing:1px;font-weight:bold}" +
            ".grid{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:16px}" +
            "a.tile{display:block;min-height:88px;text-decoration:none;color:white;padding:17px 14px;border-radius:19px;border:1px solid rgba(255,255,255,.18);background:rgba(255,255,255,.09);font-size:14px;font-weight:bold;box-shadow:0 10px 24px rgba(0,0,0,.18);transition:transform .15s ease}" +
            ".tile span{display:block;color:#c2c8dc;font-size:10px;font-weight:normal;letter-spacing:1.2px;margin-top:8px}" +
            "a.tile.anime{background:linear-gradient(135deg,rgba(104,49,180,.94),rgba(28,38,93,.92));border-color:rgba(205,166,255,.55)}" +
            "a.tile.cinema{background:linear-gradient(135deg,rgba(190,20,43,.96),rgba(53,9,25,.96));border-color:rgba(255,125,142,.55)}" +
            ".tile .tag{display:inline-block;margin-top:0;margin-bottom:9px;padding:4px 8px;border-radius:20px;background:rgba(255,255,255,.16);color:#fff;font-size:9px;letter-spacing:1.5px}" +
            ".foot{margin-top:28px;text-align:center;color:#8993ae;font-size:11px}" +
            "a.tile:focus,a.tile:focus-visible{outline:3px solid #fff;outline-offset:4px;transform:scale(1.03)}" +
            "@media (min-width:900px){body{padding:46px 7vw}.grid{grid-template-columns:repeat(3,minmax(0,1fr));gap:20px}a.tile{min-height:145px;padding:24px;font-size:19px}.tile span{font-size:12px}h1{font-size:46px}.glass{max-width:1100px}}" +
            "</style></head><body>" +
            "<div class='brand'>KING BROWSER</div><h1>Your web.<br>Under your control.</h1>" +
            "<div class='sub'>Fast, simple and private browsing with a clean glass-style interface.</div>" +
            "<div class='glass'><div class='status'>" + status + "</div>" +
            "<div style='font-size:18px;font-weight:bold;margin-top:15px'>Welcome back</div>" +
            "<div class='sub' style='margin-top:6px'>Use the address bar above to search or open any website.</div></div>" +
            "<div class='grid'>" +
            "<a class='tile' href='https://www.google.com'>Google<span>Search the web</span></a>" +
            "<a class='tile' href='https://www.youtube.com'>YouTube<span>Videos and music</span></a>" +
            "<a class='tile' href='https://www.wikipedia.org'>Wikipedia<span>Explore knowledge</span></a>" +
            "<a class='tile' href='https://www.instagram.com'>Instagram<span>Social updates</span></a>" +
            "<a class='tile anime' href='https://subdubanime.site'><span class='tag'>ANIME WORLD</span>Sub Dub Anime<span>ANIME • DUBBED • SUBTITLED</span></a>" +
            "<a class='tile cinema' href='https://allsitehub.site'><span class='tag'>CINEMA HUB</span>All Site Hub<span>MOVIES • SERIES • STREAMING</span></a>" +
            "</div><div class='foot'>Tap the ⋮ button above for browser settings</div></body></html>";
        webView.loadDataWithBaseURL(HOME_URL, html, "text/html", "UTF-8", null);
    }

    private String loadImageDataUri(String resourceName) {
        int resourceId = getResources().getIdentifier(resourceName, "drawable", getPackageName());
        if (resourceId == 0) return "";
        try (InputStream input = getResources().openRawResource(resourceId);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
        } catch (IOException exception) {
            return "";
        }
    }

    private void showSettings() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(8));
        content.setBackgroundColor(Color.rgb(20, 23, 38));

        TextView intro = new TextView(this);
        intro.setText("Privacy and browser controls");
        intro.setTextColor(Color.LTGRAY);
        intro.setTextSize(13);
        intro.setPadding(0, 0, 0, dp(12));
        content.addView(intro);

        Switch adBlock = new Switch(this);
        adBlock.setText("Built-in AdBlock");
        adBlock.setTextColor(Color.WHITE);
        adBlock.setTextSize(16);
        adBlock.setChecked(preferences.getBoolean(PREF_ADBLOCK, true));
        content.addView(adBlock);

        TextView adHint = settingHint("Blocks requests to common advertising domains. Enabled by default.");
        content.addView(adHint);

        Switch tracking = new Switch(this);
        tracking.setText("Tracking protection");
        tracking.setTextColor(Color.WHITE);
        tracking.setTextSize(16);
        tracking.setChecked(preferences.getBoolean(PREF_TRACKING, true));
        LinearLayout.LayoutParams trackingParams = new LinearLayout.LayoutParams(-1, -2);
        trackingParams.topMargin = dp(14);
        content.addView(tracking, trackingParams);

        content.addView(settingHint("Blocks requests to a small list of known tracking domains."));

        TextView popup = new TextView(this);
        popup.setText("Pop-up windows     ON");
        popup.setTextColor(Color.WHITE);
        popup.setTextSize(16);
        popup.setPadding(0, dp(18), 0, dp(4));
        content.addView(popup);
        content.addView(settingHint("JavaScript-created secondary windows are blocked by default."));

        TextView counter = new TextView(this);
        counter.setText("Blocked requests this session: " + blockedRequests.get());
        counter.setTextColor(Color.rgb(150, 225, 205));
        counter.setTextSize(14);
        counter.setPadding(0, dp(18), 0, dp(12));
        content.addView(counter);

        Button whitelist = makeButton(whitelistButtonLabel(), Color.rgb(65, 76, 112));
        content.addView(whitelist, new LinearLayout.LayoutParams(-1, dp(46)));
        whitelist.setOnClickListener(v -> {
            String host = currentHost();
            if (host == null) {
                Toast.makeText(this, "Open a website first to manage its whitelist.", Toast.LENGTH_SHORT).show();
                return;
            }
            Set<String> sites = getWhitelist();
            if (sites.contains(host)) sites.remove(host);
            else sites.add(host);
            preferences.edit().putStringSet(PREF_WHITELIST, sites).apply();
            whitelist.setText(whitelistButtonLabel());
            Toast.makeText(this, sites.contains(host) ? "Website added to whitelist" : "Website removed from whitelist", Toast.LENGTH_SHORT).show();
        });

        adBlock.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(PREF_ADBLOCK, checked).apply();
            adHint.setText(checked ? "AdBlock is ON. Common advertising domains are blocked." : "AdBlock is OFF.");
            refreshCurrentPage();
        });
        tracking.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(PREF_TRACKING, checked).apply();
            refreshCurrentPage();
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("KING BROWSER SETTINGS")
            .setView(scroll)
            .setPositiveButton("Done", null)
            .create();
        dialog.setOnShowListener(v -> {
            dialog.getWindow().setBackgroundDrawable(rounded(Color.rgb(20, 23, 38), dp(20)));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.rgb(177, 157, 255));
        });
        dialog.show();
    }

    private String whitelistButtonLabel() {
        String host = currentHost();
        if (host == null) return "Allow ads on this website";
        return isWhitelisted(host) ? "Remove " + host + " from whitelist" : "Allow ads on " + host;
    }

    private String currentHost() {
        if (webView == null || webView.getUrl() == null) return null;
        String host = Uri.parse(webView.getUrl()).getHost();
        if (host == null || host.equals("kingbrowser.home")) return null;
        return host.toLowerCase(Locale.ROOT);
    }

    private TextView settingHint(String value) {
        TextView hint = new TextView(this);
        hint.setText(value);
        hint.setTextColor(Color.rgb(167, 174, 194));
        hint.setTextSize(12);
        hint.setPadding(0, dp(4), 0, dp(2));
        return hint;
    }

    private void refreshCurrentPage() {
        if (webView != null && webView.getUrl() != null && !webView.getUrl().equals(HOME_URL)) {
            webView.reload();
        } else if (webView != null) {
            showHomePage();
        }
    }

    private void loadAddress() {
        String value = addressBar.getText().toString().trim();
        if (value.isEmpty()) return;
        String url;
        if (value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            url = value;
        } else if (value.contains(" ") || !value.contains(".")) {
            url = "https://www.google.com/search?q=" + Uri.encode(value);
        } else {
            url = "https://" + value;
        }
        webView.loadUrl(url);
        addressBar.clearFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
    }

    private Button makeButton(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setBackground(rounded(color, dp(14)));
        return button;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), Color.argb(35, 255, 255, 255));
        return drawable;
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
