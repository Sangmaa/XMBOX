package com.fongmi.android.tv.download;

import android.media.MediaMetadataRetriever;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.db.AppDatabase;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** 单个下载任务：解析状态、选择下载器、写回数据库。 */
public final class DownloadTask implements Runnable {

    public interface Callback {
        void onStart(Download download);

        void onProgress(Download download);

        void onEnd(Download download);
    }

    private final String id;
    private final Callback callback;
    private final Object lock = new Object();
    private volatile boolean cancelled;
    private volatile boolean paused;
    private volatile boolean discard;
    private Download current;
    private long lastProgressSave;
    private long lastBytes;
    private long lastBytesTime;
    private int lastPercent = -1;

    public DownloadTask(String id, Callback callback) {
        this.id = id;
        this.callback = callback;
    }

    public String getId() {
        return id;
    }

    public Download getDownload() {
        return current;
    }

    public void cancel(boolean pause) {
        paused = pause;
        cancelled = true;
    }

    /** 删除任务：停止下载且不回写数据库。 */
    public void discard() {
        discard = true;
        cancelled = true;
    }

    @Override
    public void run() {
        Download download = AppDatabase.get().getDownloadDao().find(id);
        if (download == null || discard) {
            finish(null);
            return;
        }
        current = download;
        if (download.isCompleted() && download.fileExists()) {
            finish(download);
            return;
        }
        if (download.isDownloading() && !download.fileExists()) {
            // 上次进程被杀留下的“下载中”记录
            download.setProgress(0);
        }
        download.setStatusInt(Download.STATUS_DOWNLOADING);
        download.setStatus("downloading");
        download.setError("");
        download.setSpeed(0);
        download.save();
        if (callback != null) callback.onStart(download);

        try {
            File root = DownloadStorage.root();
            File target = DownloadFile.target(root, download.getVodName(), download.getEpisodeName(), download.getUrl());
            download.setLocalPath(target.getAbsolutePath());
            Map<String, String> headers = parseHeaders(download.getHeader());
            File part = DownloadFile.part(target);
            File parts = DownloadFile.partsDir(target);

            DirectDownloader.Progress progress = new DirectDownloader.Progress() {
                @Override
                public void onProgress(long downloaded, long total, long speed) throws IOException {
                    updateProgress(download, downloaded, total, speed);
                }

                @Override
                public boolean isCancelled() {
                    return cancelled;
                }
            };

            if (DownloadFile.isHls(download.getUrl())) {
                new HlsDownloader(download.getUrl(), headers, parts, new HlsDownloader.Progress() {
                    @Override
                    public void onProgress(int percent, long downloadedBytes) throws IOException {
                        updateProgress(download, downloadedBytes, 0, 0, percent);
                    }

                    @Override
                    public boolean isCancelled() {
                        return cancelled;
                    }
                }).download(target);
            } else {
                try {
                    DirectDownloader.download(download.getUrl(), headers, part, target, progress);
                } catch (DirectDownloader.PlaylistException e) {
                    new HlsDownloader(download.getUrl(), headers, parts, new HlsDownloader.Progress() {
                        @Override
                        public void onProgress(int percent, long downloadedBytes) throws IOException {
                            updateProgress(download, downloadedBytes, 0, 0, percent);
                        }

                        @Override
                        public boolean isCancelled() {
                            return cancelled;
                        }
                    }).download(target);
                }
            }
            if (cancelled) throw new DirectDownloader.CancelledException();
            download.setProgress(100);
            download.setStatusInt(Download.STATUS_COMPLETED);
            download.setStatus("completed");
            download.setSpeed(0);
            download.setError("");
            download.setDuration(duration(target));
            download.setTotalBytes(target.length());
            download.setDownloaded(target.length());
            download.setSpeed(0);
            download.save();
        } catch (DirectDownloader.CancelledException e) {
            download.setSpeed(0);
            if (discard) {
                // 记录已被用户删除，不回写
            } else if (paused) {
                download.setStatusInt(Download.STATUS_PAUSED);
                download.setStatus("paused");
                download.setError("已暂停");
                download.save();
            } else {
                download.setStatusInt(Download.STATUS_FAILED);
                download.setStatus("failed");
                download.setError("已取消");
                download.save();
            }
        } catch (Exception e) {
            com.github.catvod.utils.Logger.e("DownloadTask", e);
            download.setSpeed(0);
            if (!discard) {
                download.setStatusInt(Download.STATUS_FAILED);
                download.setStatus("failed");
                download.setError(e.getMessage() == null ? "下载失败" : e.getMessage());
                download.save();
            }
        } finally {
            finish(download);
        }
    }

    private void updateProgress(Download download, long downloaded, long total, long speed) {
        updateProgress(download, downloaded, total, speed, -1);
    }

    private void updateProgress(Download download, long downloaded, long total, long speed, int percentOverride) {
        if (downloaded > 0) {
            long now = System.currentTimeMillis();
            if (speed <= 0 && lastBytes > 0 && now > lastBytesTime) speed = Math.max(0, (downloaded - lastBytes) * 1000 / (now - lastBytesTime));
            lastBytes = downloaded;
            lastBytesTime = now;
        }
        int percent;
        if (percentOverride >= 0) {
            percent = percentOverride;
        } else if (total > 0) {
            percent = (int) Math.min(99, downloaded * 100 / total);
        } else {
            percent = Math.min(99, download.getProgress());
        }
        synchronized (lock) {
            download.setProgress(percent);
            download.setSpeed(speed);
            if (downloaded > 0) download.setDownloaded(downloaded);
            if (total > 0) download.setTotalBytes(total);
            long now = System.currentTimeMillis();
            if (now - lastProgressSave >= 1000) {
                lastPercent = percent;
                lastProgressSave = now;
                download.save();
                if (callback != null) callback.onProgress(download);
            }
        }
    }

    private void finish(Download download) {
        if (callback != null) callback.onEnd(download);
    }

    private static long duration(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return value == null ? 0 : Long.parseLong(value);
        } catch (Exception e) {
            return 0;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    public static Map<String, String> parseHeaders(String json) {
        Map<String, String> headers = new HashMap<>();
        if (json == null || json.isEmpty()) return headers;
        try {
            Map<?, ?> map = App.gson().fromJson(json, Map.class);
            if (map != null) for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) headers.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        } catch (Exception ignored) {
        }
        return headers;
    }

    public static String toHeadersJson(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return "";
        return App.gson().toJson(headers);
    }
}
