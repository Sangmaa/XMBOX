package com.fongmi.android.tv.download;

import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.service.DownloadService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把用户选中的剧集解析成可下载地址并写入数据库，然后交给 DownloadService。
 */
public final class DownloadEnqueuer {

    public interface Callback {
        void onResult(int added, int skipped, int failed);
    }

    private DownloadEnqueuer() {
    }

    public static void enqueue(String key, String flag, String vodId, String vodName, String vodPic, List<Episode> episodes, Callback callback) {
        enqueue(key, flag, vodId, vodName, vodPic, episodes, null, null, callback);
    }

    public static void enqueueOne(String key, String flag, String vodId, String vodName, String vodPic, Episode episode, Callback callback) {
        List<Episode> episodes = new ArrayList<>();
        episodes.add(episode);
        enqueue(key, flag, vodId, vodName, vodPic, episodes, null, null, callback);
    }

    /**
     * @param directUrl     当前正在播放的媒体地址（可为空）。非空时直接使用，避免重新解析失败。
     * @param directHeaders 与 directUrl 配套的请求头。
     */
    public static void enqueueOne(String key, String flag, String vodId, String vodName, String vodPic, Episode episode, String directUrl, Map<String, String> directHeaders, Callback callback) {
        List<Episode> episodes = new ArrayList<>();
        episodes.add(episode);
        enqueue(key, flag, vodId, vodName, vodPic, episodes, directUrl, directHeaders, callback);
    }

    private static void enqueue(String key, String flag, String vodId, String vodName, String vodPic, List<Episode> episodes, String directUrl, Map<String, String> directHeaders, Callback callback) {
        App.execute(() -> {
            int added = 0, skipped = 0, failed = 0;
            List<String> ids = new ArrayList<>();
            for (Episode episode : episodes) {
                try {
                    String id = idFor(key, vodId, episode.getUrl());
                    Download existing = Download.find(id);
                    if (existing != null && existing.isCompleted() && existing.fileExists()) {
                        skipped++;
                        continue;
                    }
                    DownloadResolver.Resolved resolved;
                    boolean direct = episodes.size() == 1 && directUrl != null && !directUrl.isEmpty();
                    if (direct) {
                        resolved = new DownloadResolver.Resolved(directUrl, directHeaders);
                    } else {
                        resolved = DownloadResolver.resolve(key, flag, episode.getUrl());
                    }
                    if (resolved.url == null || resolved.url.isEmpty()) {
                        failed++;
                        continue;
                    }
                    Download download = existing == null ? new Download(id, vodPic, vodName, resolved.url) : existing;
                    download.setVodPic(vodPic);
                    download.setVodName(vodName);
                    download.setVodId(vodId);
                    download.setEpisodeName(episode.getName());
                    download.setUrl(resolved.url);
                    download.setHeader(DownloadTask.toHeadersJson(resolved.headers));
                    download.setCreateTime(System.currentTimeMillis());
                    download.setProgress(0);
                    download.setSpeed(0);
                    download.setError("");
                    download.setStatusInt(Download.STATUS_PENDING);
                    download.setStatus("pending");
                    download.save();
                    ids.add(id);
                    added++;
                } catch (Exception e) {
                    Log.e("XMBOX", "enqueue failed for " + episode.getName(), e);
                    failed++;
                }
            }
            int addedCount = added, skippedCount = skipped, failedCount = failed;
            App.post(() -> {
                if (callback != null) callback.onResult(addedCount, skippedCount, failedCount);
            });
            if (!ids.isEmpty()) DownloadService.start(App.get(), ids);
        });
    }

    private static String idFor(String key, String vodId, String episodeUrl) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((key + "|" + vodId + "|" + episodeUrl).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) sb.append(String.format("%02x", bytes[i]));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf((key + "|" + vodId + "|" + episodeUrl).hashCode());
        }
    }
}
