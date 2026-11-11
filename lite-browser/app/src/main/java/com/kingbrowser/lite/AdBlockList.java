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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class AdBlockList {
    private static final String LIST_URL =
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts";
    private static final Set<String> HOSTS = ConcurrentHashMap.newKeySet();
    private static volatile boolean initialized = false;

    private AdBlockList() {}

    static void initialize(Context context) {
        if (initialized) return;
        initialized = true;
        File cache = new File(context.getFilesDir(), "community-ad-hosts.txt");
        Thread worker = new Thread(() -> {
            readIntoMemory(cache);
            HttpURLConnection connection = null;
            File temp = new File(context.getFilesDir(), "community-ad-hosts.tmp");
            try {
                connection = (HttpURLConnection) new URL(LIST_URL).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("User-Agent", "KING-BROWSER-Lite-AdBlock/1.0");
                connection.setInstanceFollowRedirects(true);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8));
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(temp), StandardCharsets.UTF_8))) {
                    String line;
                    int count = 0;
                    while ((line = reader.readLine()) != null && count < 150000) {
                        String parsed = parseHostLine(line);
                        if (parsed != null) {
                            writer.write(parsed);
                            writer.newLine();
                            count++;
                        }
                    }
                }
                if (temp.length() > 0) {
                    if (cache.exists()) cache.delete();
                    if (temp.renameTo(cache)) readIntoMemory(cache);
                }
            } catch (Exception ignored) {
                if (temp.exists()) temp.delete();
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "King-AdBlock-List");
        worker.setDaemon(true);
        worker.start();
    }

    static boolean blocks(String host) {
        if (host == null || host.isEmpty()) return false;
        String current = host.toLowerCase(java.util.Locale.ROOT);
        while (current.contains(".")) {
            if (HOSTS.contains(current)) return true;
            int dot = current.indexOf('.');
            current = current.substring(dot + 1);
        }
        return HOSTS.contains(current);
    }

    private static void readIntoMemory(File file) {
        if (!file.isFile()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String host = parseHostLine(line);
                if (host != null) HOSTS.add(host);
            }
        } catch (Exception ignored) {}
    }

    private static String parseHostLine(String line) {
        if (line == null) return null;
        String value = line.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty() || value.startsWith("#") || value.startsWith("!") ||
                value.startsWith("[")) return null;
        String[] parts = value.split("\\s+");
        String candidate = parts.length > 1 ? parts[parts.length - 1] : parts[0];
        if (candidate.equals("localhost") || candidate.endsWith(".localhost") ||
                candidate.equals("broadcasthost") || candidate.equals("local")) return null;
        if (candidate.length() > 253 || !candidate.contains(".")) return null;
        if (!candidate.matches("[a-z0-9.-]+") || candidate.startsWith(".") ||
                candidate.endsWith(".") || candidate.contains("..")) return null;
        return candidate;
    }
}
