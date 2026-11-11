package com.kingbrowser.lite;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

final class CrashLog {
    private static final String TAG = "KING-BROWSER";
    private static final String FILE = "king-browser-diagnostics.txt";
    private static volatile boolean installed = false;
    private CrashLog() {}

    static synchronized void install(Context context) {
        if (installed) return;
        installed = true;
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            write(context, "FATAL uncaught exception on " + thread.getName(), error);
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    static synchronized void write(Context context, String message) {
        write(context, message, null);
    }

    static synchronized void write(Context context, String message, Throwable error) {
        try (FileWriter writer = new FileWriter(new File(context.getFilesDir(), FILE), true)) {
            writer.write(System.currentTimeMillis() + " " + message + "\n");
            if (error != null) {
                writer.write(Log.getStackTraceString(error));
                writer.write("\n");
            }
        } catch (IOException ignored) {
            Log.e(TAG, "Could not persist diagnostic log", ignored);
        }
        if (error != null) Log.e(TAG, message, error);
        else Log.i(TAG, message);
    }

    static synchronized String read(Context context) {
        File file = new File(context.getFilesDir(), FILE);
        if (!file.isFile()) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append('\n');
        } catch (IOException error) {
            return "Unable to read diagnostic log: " + error.getMessage();
        }
        return result.toString();
    }
}
