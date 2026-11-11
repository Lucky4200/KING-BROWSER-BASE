package com.kingbrowser.lite;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TvChannelsActivity extends Activity {
    private static final String BASE = "https://iptv-org.github.io/iptv/countries/";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout channelList;
    private TextView status;
    private String selectedCode = "pk";

    private static final String[][] COUNTRIES = {
        {"🇵🇰 Pakistan", "pk"}, {"🇮🇳 India", "in"},
        {"🇧🇩 Bangladesh", "bd"}, {"🇸🇦 Saudi Arabia", "sa"}
    };

    private static class Channel {
        final String name;
        final String url;
        Channel(String name, String url) { this.name = name; this.url = url; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
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
            Button b = button(country[0]);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(46));
            p.rightMargin = dp(8);
            countryRow.addView(b, p);
            b.setOnClickListener(v -> loadCountry(country[1]));
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
        loadCountry("pk");
    }

    private void loadCountry(String code) {
        selectedCode = code;
        channelList.removeAllViews();
        status.setText("Loading channel list…");
        worker.execute(() -> {
            ArrayList<Channel> parsed = new ArrayList<>();
            Set<String> seenUrls = new HashSet<>();
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(BASE + code + ".m3u").openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(18000);
                connection.setRequestProperty("User-Agent", "KING-BROWSER-Lite");
                try (InputStream input = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
                    String line, name = null;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("\uFEFF")) line = line.substring(1);
                        if (line.startsWith("#EXTINF")) {
                            int comma = line.lastIndexOf(',');
                            name = comma >= 0 ? line.substring(comma + 1).trim() : "Live channel";
                        } else if (!line.isEmpty() && !line.startsWith("#") && name != null) {
                            if (line.startsWith("https://") && seenUrls.add(line)) {
                                parsed.add(new Channel(name, line));
                            }
                            name = null;
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
            final ArrayList<Channel> result = parsed;
            runOnUiThread(() -> {
                if (isFinishing() || !selectedCode.equals(code)) return;
                channelList.removeAllViews();
                if (result.isEmpty()) {
                    status.setText("No HTTPS streams are currently listed for this country. Try another country or refresh later.");
                    return;
                }
                status.setText(result.size() + " available HTTPS stream entries • Live availability may vary");
                for (Channel channel : result) {
                    Button item = button("▶   " + channel.name);
                    item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                    item.setMaxLines(2);
                    item.setPadding(dp(16), 0, dp(12), 0);
                    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(56));
                    p.bottomMargin = dp(8);
                    channelList.addView(item, p);
                    item.setOnClickListener(v -> {
                        Intent intent = new Intent(this, TvPlayerActivity.class);
                        intent.putExtra("stream_url", channel.url);
                        intent.putExtra("channel_name", channel.name);
                        startActivity(intent);
                    });
                }
            });
        });
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
        worker.shutdownNow();
        super.onDestroy();
    }
}