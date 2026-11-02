package com.fongmi.android.tv.download;

import com.github.catvod.net.OkHttp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Request;
import okhttp3.Response;

/** HLS 下载器：拉取 m3u8、下载全部分片（支持 AES-128 加密与 fMP4 初始化段），合并为本地文件。 */
public final class HlsDownloader {

    public interface Progress {
        void onProgress(int percent, long downloadedBytes) throws IOException;

        boolean isCancelled();
    }

    private final String url;
    private final Map<String, String> headers;
    private final File partsDir;
    private final Progress progress;
    private final Map<String, byte[]> keys = new HashMap<>();

    public HlsDownloader(String url, Map<String, String> headers, File partsDir, Progress progress) {
        this.url = url;
        this.headers = headers;
        this.partsDir = partsDir;
        this.progress = progress;
    }

    public void download(File target) throws IOException {
        HlsPlaylist playlist = HlsPlaylist.parse(fetchText(url), url);
        if (playlist.isMaster()) {
            String variant = playlist.bestVariant();
            if (variant == null) throw new IOException("播放列表没有可用清晰度");
            playlist = HlsPlaylist.parse(fetchText(variant), variant);
        }
        if (playlist.isEmpty()) throw new IOException("播放列表为空，可能不是点播视频");
        deleteRecursive(partsDir);
        if (!partsDir.exists() && !partsDir.mkdirs()) throw new IOException("无法创建分片目录");

        int total = playlist.segments.size();
        int done = 0;
        long downloadedBytes = 0;
        byte[] init = null;
        if (playlist.initUri != null) {
            init = fetchBytes(playlist.initUri, playlist.initRangeOffset, playlist.initRangeLength);
            downloadedBytes += init.length;
        }
        for (int i = 0; i < total; i++) {
            if (progress.isCancelled()) throw new DirectDownloader.CancelledException();
            File file = new File(partsDir, String.format("seg_%05d", i));
            if (file.isFile() && file.length() > 0) {
                // 断点续传：上次已下载的分片直接复用
                downloadedBytes += file.length();
                done++;
                progress.onProgress(Math.min(95, done * 95 / Math.max(1, total)), downloadedBytes);
                continue;
            }
            HlsPlaylist.Segment segment = playlist.segments.get(i);
            byte[] data = fetchSegmentWithRetry(segment, i);
            if (i == 0 && isImage(data)) throw new IOException("该视频源的分片只有图片占位，无法下载为可播放视频");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(data);
            }
            downloadedBytes += data.length;
            done++;
            progress.onProgress(Math.min(95, done * 95 / Math.max(1, total)), downloadedBytes);
        }
        merge(target, init, total);
        progress.onProgress(100, downloadedBytes);
        deleteRecursive(partsDir);
    }

    private byte[] fetchSegmentWithRetry(HlsPlaylist.Segment segment, int index) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (progress.isCancelled()) throw new DirectDownloader.CancelledException();
            try {
                byte[] data = fetchBytes(segment.uri, segment.rangeOffset, segment.rangeLength);
                if (data.length == 0) throw new IOException("分片为空");
                if (segment.keyUri != null) data = decrypt(data, segment);
                if (data.length == 0) throw new IOException("分片解密失败");
                return stripDisguise(data);
            } catch (DirectDownloader.CancelledException e) {
                throw e;
            } catch (Exception e) {
                last = e instanceof IOException ? (IOException) e : new IOException(e.getMessage(), e);
                try {
                    Thread.sleep(800L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new DirectDownloader.CancelledException();
                }
            }
        }
        throw new IOException("分片 " + (index + 1) + " 下载失败: " + (last == null ? "" : last.getMessage()));
    }

    private byte[] decrypt(byte[] data, HlsPlaylist.Segment segment) throws Exception {
        byte[] key = key(segment.keyUri);
        byte[] iv = segment.iv != null ? hex(segment.iv) : sequenceIv(segment.sequence);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        try {
            return cipher.doFinal(data);
        } catch (Exception e) {
            // 少量服务器使用无填充 CBC
            Cipher noPadding = Cipher.getInstance("AES/CBC/NoPadding");
            noPadding.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return noPadding.doFinal(data);
        }
    }

    private synchronized byte[] key(String keyUri) throws IOException {
        byte[] cached = keys.get(keyUri);
        if (cached != null) return cached;
        byte[] key = fetchBytes(keyUri, -1, -1);
        keys.put(keyUri, key);
        return key;
    }

    private static byte[] sequenceIv(int sequence) {
        byte[] iv = new byte[16];
        long value = sequence;
        for (int i = 15; i >= 8; i--) {
            iv[i] = (byte) (value & 0xff);
            value >>= 8;
        }
        return iv;
    }

    private static byte[] hex(String value) {
        String clean = value.startsWith("0x") || value.startsWith("0X") ? value.substring(2) : value;
        int length = clean.length() / 2;
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) bytes[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }

    private String fetchText(String uri) throws IOException {
        return new String(fetchBytes(uri, -1, -1, true), StandardCharsets.UTF_8);
    }

    private byte[] fetchBytes(String uri, long rangeOffset, long rangeLength) throws IOException {
        return fetchBytes(uri, rangeOffset, rangeLength, false);
    }

    private byte[] fetchBytes(String uri, long rangeOffset, long rangeLength, boolean text) throws IOException {
        Request.Builder builder = new Request.Builder().url(uri);
        if (headers != null) for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty()) continue;
            try {
                builder.header(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (builder.build().header("User-Agent") == null) builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
        if (rangeLength > 0) builder.header("Range", "bytes=" + Math.max(0, rangeOffset) + "-" + (Math.max(0, rangeOffset) + rangeLength - 1));
        okhttp3.OkHttpClient client = OkHttp.client().newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + " " + response.message());
            if (response.body() == null) throw new IOException("响应为空");
            return text ? response.body().string().getBytes(StandardCharsets.UTF_8) : response.body().bytes();
        }
    }

    private void merge(File target, byte[] init, int count) throws IOException {
        DownloadStorage.ensureParent(target);
        File temp = new File(target.getParentFile(), target.getName() + ".merge");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            if (init != null) out.write(init);
            byte[] buffer = new byte[64 * 1024];
            for (int i = 0; i < count; i++) {
                File file = new File(partsDir, String.format("seg_%05d", i));
                try (InputStream in = new java.io.FileInputStream(file)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                }
            }
            out.flush();
        }
        DirectDownloader.move(temp, target);
    }

    /**
     * 部分站点把 TS 分片伪装成图片分发：文件前 N 字节是 PNG/JPEG 头，后面才是完整的 188 字节 TS 包。
     * 播放器能正常播放，但直接按图片判断会误杀。这里定位 TS 同步字节并去掉伪装头。
     */
    static byte[] stripDisguise(byte[] data) {
        if (data.length < 800 || data[0] == 0x47) return data;
        int limit = Math.min(4096, data.length - 564);
        for (int offset = 1; offset < limit; offset++) {
            if (data[offset] == 0x47 && data[offset + 188] == 0x47 && data[offset + 376] == 0x47 && data[offset + 564] == 0x47) {
                byte[] stripped = new byte[data.length - offset];
                System.arraycopy(data, offset, stripped, 0, stripped.length);
                return stripped;
            }
        }
        return data;
    }

    private static boolean isImage(byte[] data) {
        if (data == null || data.length < 12) return false;
        if ((data[0] & 0xff) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') return true;
        if ((data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8) return true;
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F') return true;
        return data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' && data[8] == 'W';
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursive(child);
        }
        file.delete();
    }
}
