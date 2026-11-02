package com.fongmi.android.tv.download;

import com.github.catvod.net.OkHttp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Request;
import okhttp3.Response;

/** 直链（mp4 等）下载器：支持 Range 断点续传、限速统计与取消。 */
public final class DirectDownloader {

    public interface Progress {
        void onProgress(long downloaded, long total, long speed) throws IOException;

        boolean isCancelled();
    }

    public static class CancelledException extends IOException {
        public CancelledException() {
            super("已取消");
        }
    }

    /** 响应表明是 m3u8 播放列表，需要切换到 HLS 下载。 */
    public static class PlaylistException extends IOException {
        public PlaylistException(String message) {
            super(message);
        }
    }

    private DirectDownloader() {
    }

    public static void download(String url, Map<String, String> headers, File part, File target, Progress progress) throws IOException {
        DownloadStorage.ensureParent(part);
        long start = part.exists() ? part.length() : 0;
        Request.Builder builder = new Request.Builder().url(url);
        if (headers != null) for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty()) continue;
            try {
                builder.header(key, entry.getValue() == null ? "" : entry.getValue());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (builder.build().header("User-Agent") == null) builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
        if (start > 0) builder.header("Range", "bytes=" + start + "-");

        okhttp3.OkHttpClient client = OkHttp.client().newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        try (Response response = client.newCall(builder.build()).execute()) {
            if (response.code() == 416) {
                // 本地已下载部分与服务器不一致，删除后重来
                if (!part.delete()) throw new IOException("无法删除临时文件");
                throw new IOException("断点位置无效，请重新下载");
            }
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + " " + response.message());
            if (isPlaylist(response)) {
                part.delete();
                throw new PlaylistException("HLS 播放列表");
            }
            long contentLength = response.body() == null ? -1 : response.body().contentLength();
            boolean append = response.code() == 206 && start > 0;
            long downloaded = append ? start : 0;
            long total = contentLength > 0 ? downloaded + contentLength : 0;
            if (!append && start > 0) {
                // 服务器不支持 Range，从头开始
                part.delete();
                start = 0;
            }
            if (progress.isCancelled()) throw new CancelledException();
            try (InputStream in = response.body().byteStream();
                 FileOutputStream out = new FileOutputStream(part, append)) {
                byte[] buffer = new byte[64 * 1024];
                long lastReport = 0;
                long windowBytes = 0;
                long windowTime = System.currentTimeMillis();
                long lastData = System.currentTimeMillis();
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (progress.isCancelled()) throw new CancelledException();
                    out.write(buffer, 0, read);
                    downloaded += read;
                    windowBytes += read;
                    long now = System.currentTimeMillis();
                    lastData = now;
                    if (now - lastReport >= 700) {
                        long elapsed = Math.max(1, now - windowTime);
                        long speed = windowBytes * 1000 / elapsed;
                        progress.onProgress(downloaded, total, speed);
                        lastReport = now;
                        if (now - windowTime >= 1000) {
                            windowBytes = 0;
                            windowTime = now;
                        }
                    }
                    if (now - lastData > 60_000) throw new IOException("网络长时间无响应，已中断");
                }
                out.flush();
            }
            if (total > 0 && downloaded < total) throw new IOException("下载不完整 " + downloaded + "/" + total);
            progress.onProgress(downloaded, total == 0 ? downloaded : total, 0);
        }
        move(part, target);
    }

    private static boolean isPlaylist(Response response) throws IOException {
        String type = response.header("Content-Type");
        if (type != null) {
            String lower = type.toLowerCase();
            if (lower.contains("mpegurl") || lower.contains("m3u8")) return true;
        }
        String path = response.request().url().encodedPath();
        if (path != null && path.toLowerCase().contains(".m3u8")) return true;
        // 不少 CDN 把播放列表以 text/plain 或 octet-stream 返回，嗅探正文开头最可靠
        if (response.body() != null) {
            String text = new String(response.peekBody(64).bytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (text.startsWith("\uFEFF")) text = text.substring(1).trim();
            if (text.startsWith("#EXTM3U") || text.startsWith("#EXT-X-")) return true;
        }
        return false;
    }

    static void move(File part, File target) throws IOException {
        DownloadStorage.ensureParent(target);
        if (target.exists() && !target.delete()) throw new IOException("无法覆盖已有文件");
        if (!part.renameTo(target)) {
            try (InputStream in = new java.io.FileInputStream(part);
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            if (!part.delete()) throw new IOException("无法删除临时文件");
        }
    }

    static long length(File file) {
        if (file == null || !file.exists()) return 0;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            return raf.length();
        } catch (IOException e) {
            return 0;
        }
    }
}
