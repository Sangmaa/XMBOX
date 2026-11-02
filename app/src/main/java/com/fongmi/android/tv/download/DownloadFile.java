package com.fongmi.android.tv.download;

import java.io.File;
import java.util.Locale;

/**
 * 下载文件名与展示相关的纯 Java 工具，便于在 JVM 单测中直接验证。
 */
public final class DownloadFile {

    private static final String ILLEGAL = "[\\\\/:*?\"<>|\\x00-\\x1F]";

    private DownloadFile() {
    }

    public static String sanitize(String name) {
        if (name == null) return "";
        String clean = name.replaceAll(ILLEGAL, "_").trim();
        // Windows/Android 文件名不能以点或空格结尾
        while (clean.endsWith(".") || clean.endsWith(" ")) clean = clean.substring(0, clean.length() - 1);
        if (clean.isEmpty()) clean = "video";
        if (clean.length() > 80) clean = clean.substring(0, 80).trim();
        return clean;
    }

    public static boolean isHls(String url) {
        if (url == null) return false;
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".m3u8") || lower.endsWith(".m3u") || lower.contains("playlist");
    }

    public static String extensionFor(String url) {
        if (isHls(url)) return ".mp4";
        if (url != null) {
            String path = url;
            int query = path.indexOf('?');
            if (query >= 0) path = path.substring(0, query);
            int dot = path.lastIndexOf('.');
            int slash = path.lastIndexOf('/');
            if (dot > slash && dot < path.length() - 1 && path.length() - dot <= 6) {
                String ext = path.substring(dot).toLowerCase(Locale.ROOT);
                if (ext.matches("\\.[a-z0-9]{1,5}")) return ext;
            }
        }
        return ".mp4";
    }

    /** 目录结构：根目录/剧名/集名.ext，缺集名时用 URL 的 MD5 前缀。 */
    public static String relativePath(String vodName, String episodeName, String url) {
        String vod = sanitize(vodName);
        String episode = sanitize(episodeName);
        if (episode.isEmpty()) episode = "video_" + md5(url);
        return vod + File.separator + episode + extensionFor(url);
    }

    public static File target(File root, String vodName, String episodeName, String url) {
        return new File(root, relativePath(vodName, episodeName, url));
    }

    public static File part(File target) {
        return new File(target.getParentFile(), target.getName() + ".part");
    }

    public static File partsDir(File target) {
        return new File(target.getParentFile(), target.getName() + ".parts");
    }

    public static String formatSize(long bytes) {
        if (bytes <= 0) return "0B";
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1fKB", bytes / 1024f);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1fMB", bytes / 1024f / 1024f);
        return String.format(Locale.ROOT, "%.2fGB", bytes / 1024f / 1024f / 1024f);
    }

    public static String formatSpeed(long bytesPerSecond) {
        return formatSize(bytesPerSecond) + "/s";
    }

    private static String md5(String url) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest((url == null ? "" : url).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.substring(0, 10);
        } catch (Exception e) {
            return "video";
        }
    }
}
