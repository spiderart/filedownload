package com.clevel.poc.sp.filews.util;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileTracker {

    private static final Map<String, Long> fileTimestamps = new ConcurrentHashMap<>();

    public static void register(String fileName) {
        fileTimestamps.put(fileName, System.currentTimeMillis());
    }

    public static void unregister(String fileName) {
        fileTimestamps.remove(fileName);
    }

    // Optional: run this with a scheduled job
    public static void cleanOldFiles(String dir, int maxMinutes) {
        long now = System.currentTimeMillis();
        for (String name : fileTimestamps.keySet()) {
            if ((now - fileTimestamps.get(name)) > maxMinutes * 60_000) {
                File file = new File(dir + "/" + name);
                if (file.exists()) file.delete();
                unregister(name);
            }
        }
    }
}
