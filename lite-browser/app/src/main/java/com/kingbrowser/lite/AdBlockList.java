package com.kingbrowser.lite;

import android.content.Context;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class AdBlockList {
    private static final String[] LIST_URLS = {
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        "https://easylist.to/easylist/easylist.txt",
        "https://easylist.to/easylist/easyprivacy.txt"
    };
    private static final String[] CACHE_NAMES = {
        "community-ad-hosts.txt", "easylist-domains.txt", "easyprivacy-domains.txt"
    };
    private static final Set<String> BLOCKED = ConcurrentHashMap.newKeySet();
    private static final Set<String> ALLOWED = ConcurrentHashMap.newKeySet();
    private static volatile boolean initialized = false;

    private AdBlockList() {}

    static void initialize(Context context) {
        if (initialized) return;
        initialized = true;
        for (String name : CACHE_NAMES) {
            loadCache(new File(context.getFilesDir(), name));
        }
        Thread worker = new Thread(() -> {
            for (int i = 0; i < LIST_URLS.length; i++) {
                downloadList(LIST_URLS[i], new File(context.getFilesDir(), CACHE_NAMES[i]), i == 0);
            }
        }, "King-Enhanced-AdBlock");
        worker.setDaemon(true);
        worker.start();
    }

    static boolean blocks(String host) {
        if (host == null || host.isEmpty()) return false;
        String current = host.toLowerCase(Locale.ROOT);
        String probe = current;
        while (true) {
            if (ALLOWED.contains(probe)) return false;
            int dot = probe.indexOf('.');
            if (dot < 0) break;
            probe = probe.substring(dot + 1);
        }
        probe = current;
        while (true) {
            if (BLOCKED.contains(probe)) return true;
            int dot = probe.indexOf('.');
            if (dot < 0) break;
            probe = probe.substring(dot + 1);
        }
        return false;
    }

    private static void loadCache(File file) {
        if (!file.isFile()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) addStoredRule(line);
        } catch (Exception ignored) {}
    }

    private static void downloadList(String listUrl, File cache, boolean hostsFormat) {
        HttpURLConnection connection = null;
        File temp = new File(cache.getParentFile(), cache.getName() + ".tmp");
        try {
            connection = (HttpURLConnection) new URL(listUrl).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "KING-BROWSER-Lite/1.0 AdBlock");
            connection.setRequestProperty("Accept", "text/plain,*/*");
            connection.setInstanceFollowRedirects(true);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return;
            int parsedCount = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(temp), StandardCharsets.UTF_8))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount++ < 500000) {
                    String rule = hostsFormat ? parseHostsLine(line) : parseFilterRule(line);
                    if (rule != null) {
                        writer.write(rule);
                        writer.newLine();
                        parsedCount++;
                    }
                }
            }
            if (parsedCount > 100) {
                if (cache.exists()) cache.delete();
                if (temp.renameTo(cache)) loadCache(cache);
            } else if (temp.exists()) {
                temp.delete();
            }
        } catch (Exception ignored) {
            if (temp.exists()) temp.delete();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void addStoredRule(String rule) {
        if (rule == null || rule.isEmpty()) return;
        if (rule.startsWith("@@")) ALLOWED.add(rule.substring(2));
        else BLOCKED.add(rule);
    }

    private static String parseHostsLine(String line) {
        if (line == null) return null;
        String value = line.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.startsWith("#")) return null;
        String[] parts = value.split("\\s+");
        if (parts.length < 2) return null;
        String ip = parts[0];
        if (!ip.equals("0.0.0.0") && !ip.equals("127.0.0.1") && !ip.equals("::")) return null;
        return validDomain(parts[1]) ? parts[1] : null;
    }

    // Import domain-based ABP rules from EasyList/EasyPrivacy. Cosmetic and regex
    // rules are intentionally ignored because WebView request interception is URL-based.
    private static String parseFilterRule(String line) {
        if (line == null) return null;
        String value = line.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.startsWith("!") || value.startsWith("[") ||
                value.contains("#") || value.startsWith("/") || value.startsWith("@@/")) return null;
        boolean exception = value.startsWith("@@");
        String rule = exception ? value.substring(2) : value;
        if (!rule.startsWith("||")) return null;
        String domain = rule.substring(2);
        int end = domain.length();
        for (char marker : new char[]{'^', '$', '/', '*', '|', '?'}) {
            int at = domain.indexOf(marker);
            if (at >= 0 && at < end) end = at;
        }
        domain = domain.substring(0, end);
        if (!validDomain(domain)) return null;
        return exception ? "@@" + domain : domain;
    }

    private static boolean validDomain(String domain) {
        if (domain == null || domain.length() < 4 || domain.length() > 253 ||
                !domain.contains(".") || domain.startsWith(".") || domain.endsWith(".") ||
                domain.contains("..")) return false;
        return domain.matches("[a-z0-9.-]+");
    }
}
