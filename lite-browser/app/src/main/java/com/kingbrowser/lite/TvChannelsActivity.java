package com.kingbrowser.lite;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class TvChannelsActivity extends Activity {
    private static final String BASE = "https://iptv-org.github.io/iptv/countries/";
    private static final int MAX_CHANNELS = 100;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger loadGeneration = new AtomicInteger(0);
    private LinearLayout channelList;
    private TextView status;
    private String selectedCode = "pk";
    private volatile boolean destroyed = false;

    private static final String[][] COUNTRIES = {
        {"🇵🇰 Pakistan", "pk"}, {"🇮🇳 India", "in"},
        {"🇧🇩 Bangladesh", "bd"}, {"🇸🇦 Saudi Arabia", "sa"}
    };

    private static class Channel {
        final String name;
        final String url;
        final HashMap<String, String> headers;
        Channel(String name, String url, Map<String, String> headers) {
            this.name = name;
            this.url = url;
            this.headers = new HashMap<>(headers);
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        CrashLog.install(getApplicationContext());
        CrashLog.write(getApplicationContext(), "TV channels screen opened");
        try {
            buildScreen();
            loadCountry("pk");
        } catch (Throwable error) {
            CrashLog.write(getApplicationContext(), "TV channels screen initialization failed", error);
            Toast.makeText(this, "TV screen could not open. Please check diagnostics.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void buildScreen() {
        getWindow().getDecorView().setBackgroundColor(Color.rgb(9, 13, 28));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(10));

        TextView title = new TextView(this);
        title.setText("KING LIVE TV");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Choose a country, then select a live channel");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(5), 0, dp(14));
        root.addView(subtitle);

        HorizontalScrollView countriesScroll = new HorizontalScrollView(this);
        countriesScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout countryRow = new LinearLayout(this);
        countryRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String[] country : COUNTRIES) {
            Button button = button(country[0]);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(46));
            params.rightMargin = dp(8);
            countryRow.addView(button, params);
            button.setOnClickListener(v -> loadCountry(country[1]));
        }
        countriesScroll.addView(countryRow);
        root.addView(countriesScroll);

        status = new TextView(this);
        status.setTextColor(Color.rgb(181, 190, 215));
        status.setTextSize(13);
        status.setPadding(0, dp(14), 0, dp(8));
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        channelList = new LinearLayout(this);
        channelList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(channelList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button back = button("Back to browser");
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));
        back.setOnClickListener(v -> finish());
        setContentView(root);
    }

    private void loadCountry(String code) {
        selectedCode = code;
        final int generation = loadGeneration.incrementAndGet();
        channelList.removeAllViews();
        status.setText("Loading channel list…");
        try {
            worker.execute(() -> {
                ArrayList<Channel> parsed = new ArrayList<>();
                Set<String> seenUrls = new HashSet<>();
                HttpURLConnection connection = null;
                String failure = null;
                try {
                    connection = (HttpURLConnection) new URL(BASE + code + ".m3u").openConnection();
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(12000);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent", "KING-BROWSER-Lite/1.2 (Android)");
                    connection.setRequestProperty("Accept", "application/x-mpegURL, audio/x-mpegurl, */*");
                    int response = connection.getResponseCode();
                    if (response < 200 || response >= 300) throw new IllegalStateException("Playlist HTTP " + response);
                    try (InputStream input = connection.getInputStream();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
                        String line, name = null;
                        HashMap<String, String> headers = new HashMap<>();
                        while ((line = reader.readLine()) != null && parsed.size() < MAX_CHANNELS) {
                            line = line.trim();
                            if (line.startsWith("\uFEFF")) line = line.substring(1);
                            if (line.startsWith("#EXTINF")) {
                                int comma = line.lastIndexOf(',');
                                name = comma >= 0 ? line.substring(comma + 1).trim() : "Live channel";
                                headers.clear();
                            } else if (line.startsWith("#EXTVLCOPT:")) {
                                parseVlcOption(line.substring("#EXTVLCOPT:".length()), headers);
                            } else if (!line.isEmpty() && !line.startsWith("#") && name != null) {
                                if ((line.startsWith("https://") || line.startsWith("http://")) && seenUrls.add(line)) {
                                    parsed.add(new Channel(name.isEmpty() ? "Live channel" : name, line, headers));
                                }
                                name = null;
                                headers.clear();
                            }
                        }
                    }
                } catch (Exception error) {
                    failure = error.getClass().getSimpleName() + ": " + error.getMessage();
                    CrashLog.write(getApplicationContext(), "TV playlist load failed for " + code, error);
                } finally {
                    if (connection != null) connection.disconnect();
                }
                final ArrayList<Channel> result = parsed;
                final String errorText = failure;
                runOnUiThread(() -> {
                    if (destroyed || isFinishing() || generation != loadGeneration.get()) return;
                    channelList.removeAllViews();
                    if (result.isEmpty()) {
                        status.setText("Channel list unavailable. Check internet and tap the country again.");
                        CrashLog.write(getApplicationContext(), "TV playlist had no channels for " + code + "; " + errorText);
                        return;
                    }
                    status.setText("Showing " + result.size() +
                            (result.size() >= MAX_CHANNELS ? " channels (Lite limit)" : " channels") +
                            " • Live availability may vary");
                    for (Channel channel : result) {
                        Button item = button("▶   " + channel.name);
                        item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                        item.setMaxLines(2);
                        item.setPadding(dp(16), 0, dp(12), 0);
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(56));
                        params.bottomMargin = dp(8);
                        channelList.addView(item, params);
                        item.setOnClickListener(v -> openChannel(channel));
                    }
                });
            });
        } catch (RuntimeException error) {
            CrashLog.write(getApplicationContext(), "Could not schedule TV playlist load", error);
            status.setText("TV channel loading failed. Please reopen TV.");
        }
    }

    private void parseVlcOption(String option, Map<String, String> headers) {
        int split = option.indexOf('=');
        if (split <= 0 || split == option.length() - 1) return;
        String key = option.substring(0, split).trim().toLowerCase(Locale.ROOT);
        String value = option.substring(split + 1).trim();
        if (value.length() > 2048) return;
        if ("http-user-agent".equals(key)) headers.put("User-Agent", value);
        else if ("http-referrer".equals(key) || "http-referer".equals(key)) headers.put("Referer", value);
        else if ("http-origin".equals(key)) headers.put("Origin", value);
    }

    private void openChannel(Channel channel) {
        try {
            Intent intent = new Intent(this, TvPlayerActivity.class);
            intent.putExtra("stream_url", channel.url);
            intent.putExtra("channel_name", channel.name);
            Bundle headers = new Bundle();
            for (Map.Entry<String, String> entry : channel.headers.entrySet()) {
                headers.putString(entry.getKey(), entry.getValue());
            }
            intent.putExtra("stream_headers", headers);
            CrashLog.write(getApplicationContext(), "Opening TV player: " + channel.name);
            startActivity(intent);
        } catch (Exception error) {
            CrashLog.write(getApplicationContext(), "Could not open TV player activity", error);
            Toast.makeText(this, "TV player could not open. Diagnostic log saved.", Toast.LENGTH_LONG).show();
        }
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setFocusable(true);
        b.setBackground(round(Color.rgb(39, 43, 70)));
        return b;
    }

    private GradientDrawable round(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(13));
        d.setStroke(dp(1), Color.argb(65, 190, 177, 255));
        return d;
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() {
        destroyed = true;
        loadGeneration.incrementAndGet();
        try {
            worker.shutdownNow();
        } catch (RuntimeException error) {
            CrashLog.write(getApplicationContext(), "TV channel worker shutdown failed", error);
        }
        super.onDestroy();
    }
}
