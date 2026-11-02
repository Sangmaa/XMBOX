package com.fongmi.android.tv.download;

import androidx.collection.ArrayMap;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Url;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.player.ParseJob;
import com.fongmi.android.tv.ui.custom.CustomWebView;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.player.Source;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Response;

/**
 * 在不启动播放器界面的情况下解析某一集的可下载地址。
 * 第一段逻辑与 SiteViewModel.playerContent 保持一致；若结果需要嗅探/解析（parse=1/jx=1），
 * 则复用播放器的 ParseJob 拿到真正的媒体地址和请求头，避免把解析页当成视频下载。
 */
public final class DownloadResolver {

    /** 最终可下载地址与请求头。 */
    public static final class Resolved {
        public final String url;
        public final Map<String, String> headers;

        Resolved(String url, Map<String, String> headers) {
            this.url = url;
            this.headers = headers == null ? new HashMap<>() : headers;
        }
    }

    private DownloadResolver() {
    }

    public static Resolved resolve(String key, String flag, String id) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                Result result = playerContent(key, flag, id);
                if (result.getParse() == 1 || result.getJx() == 1) result = sniff(result);
                String url = result.getRealUrl();
                if (url == null || url.isEmpty()) throw new IOException("没有取到播放地址");
                Logger.d("Download url: " + url);
                return new Resolved(url, result.getHeaders());
            } catch (Exception e) {
                last = e;
                Logger.d("DownloadResolver: attempt " + attempt + " failed: " + e);
                if (attempt < 3) Thread.sleep(1200L * attempt);
            }
        }
        throw last == null ? new IOException("解析失败") : last;
    }

    private static Result playerContent(String key, String flag, String id) throws Exception {
        Source.get().stop();
        Site site = VodConfig.get().getSite(key);
        if (site.getType() == 3) {
            Spider spider = site.recent().spider();
            String playerContent = spider.playerContent(flag, id, VodConfig.get().getFlags());
            SpiderDebug.log(playerContent);
            Result result = Result.fromJson(playerContent);
            if (result.getFlag().isEmpty()) result.setFlag(flag);
            result.setUrl(Source.get().fetch(result));
            result.setHeader(site.getHeader());
            result.setKey(key);
            return result;
        } else if (site.getType() == 4) {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("play", id);
            params.put("flag", flag);
            String playerContent = call(site, params);
            SpiderDebug.log(playerContent);
            Result result = Result.fromJson(playerContent);
            if (result.getFlag().isEmpty()) result.setFlag(flag);
            result.setUrl(Source.get().fetch(result));
            result.setHeader(site.getHeader());
            return result;
        } else if (site.isEmpty() && "push_agent".equals(key)) {
            Result result = new Result();
            result.setParse(0);
            result.setFlag(flag);
            result.setUrl(Url.create().add(id));
            result.setUrl(Source.get().fetch(result));
            return result;
        } else {
            Url url = Url.create().add(id);
            Result result = new Result();
            result.setUrl(url);
            result.setFlag(flag);
            result.setHeader(site.getHeader());
            result.setPlayUrl(site.getPlayUrl());
            result.setParse(1);
            result.setUrl(Source.get().fetch(result));
            SpiderDebug.log(result.toString());
            return result;
        }
    }

    /** 用播放器同一套解析器（嗅探/JSON 解析）换取真实媒体地址；失败时退回 WebView 嗅探。 */
    private static Result sniff(Result result) throws Exception {
        try {
            return sniffByParseJob(result);
        } catch (Exception e) {
            Logger.d("DownloadResolver: ParseJob failed, fallback to WebView: " + e);
            return sniffByWeb(result);
        }
    }

    private static Result build(Result result, String url, Map<String, String> headers) {
        Result resolved = new Result();
        resolved.setFlag(result.getFlag());
        resolved.setKey(result.getKey());
        resolved.setPlayUrl("");
        resolved.setUrl(Url.create().add(url));
        Map<String, String> merged = new HashMap<>(result.getHeaders());
        if (headers != null) merged.putAll(headers);
        resolved.setHeader(App.gson().toJsonTree(merged));
        Logger.d("Download sniffed: " + url);
        return resolved;
    }

    /** 直接加载解析页，捕获页面发出的媒体请求（与播放器嗅探同一机制）。 */
    private static Result sniffByWeb(Result result) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        String[] url = new String[]{""};
        Map<String, String>[] headers = new Map[]{new HashMap<>()};
        ParseCallback callback = new ParseCallback() {
            @Override
            public void onParseSuccess(Map<String, String> values, String value, String from) {
                url[0] = value == null ? "" : value;
                if (values != null) headers[0] = values;
                latch.countDown();
            }

            @Override
            public void onParseError() {
                latch.countDown();
            }
        };
        App.post(() -> {
            try {
                CustomWebView.create(App.get()).start(result.getKey(), "", result.getHeaders(), UrlUtil.convert(result.getUrl().v()), "", callback, true);
            } catch (Throwable t) {
                Logger.d("DownloadResolver: web sniff start failed: " + t);
                latch.countDown();
            }
        });
        if (!latch.await(45, TimeUnit.SECONDS)) throw new IOException("解析视频地址超时");
        if (url[0].isEmpty()) throw new IOException("解析视频地址失败");
        return build(result, url[0], headers[0]);
    }

    private static Result sniffByParseJob(Result result) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ParseJob[] job = new ParseJob[1];
        String[] url = new String[]{""};
        Map<String, String>[] headers = new Map[]{new HashMap<>()};
        App.post(() -> job[0] = ParseJob.create(new ParseCallback() {
            @Override
            public void onParseSuccess(Map<String, String> values, String value, String from) {
                url[0] = value == null ? "" : value;
                if (values != null) headers[0] = values;
                latch.countDown();
            }

            @Override
            public void onParseError() {
                latch.countDown();
            }
        }).start(result, true));
        if (!latch.await(40, TimeUnit.SECONDS)) {
            App.post(() -> {
                if (job[0] != null) job[0].stop();
            });
            throw new IOException("解析视频地址超时");
        }
        if (url[0].isEmpty()) throw new IOException("解析视频地址失败");
        return build(result, url[0], headers[0]);
    }

    private static String call(Site site, ArrayMap<String, String> params) throws IOException {
        if (!site.getExt().isEmpty()) params.put("extend", site.getExt());
        Call get = OkHttp.newCall(site.getApi(), site.getHeaders(), params);
        Call post = OkHttp.newCall(site.getApi(), site.getHeaders(), OkHttp.toBody(params));
        Response response = (site.getExt().length() <= 1000 ? get : post).execute();
        String result = response.body().string();
        response.close();
        return result;
    }
}
