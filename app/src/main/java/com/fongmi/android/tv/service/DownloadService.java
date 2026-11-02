package com.fongmi.android.tv.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.download.DownloadNotification;
import com.fongmi.android.tv.download.DownloadTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 下载前台服务：串行调度下载任务并维护进度通知。
 */
public class DownloadService extends Service {

    public static final String ACTION_START = "START_DOWNLOAD";
    public static final String ACTION_STOP = "CANCEL_DOWNLOAD";
    public static final String ACTION_RETRY = "RETRY_DOWNLOAD";
    public static final String EXTRA_IDS = "ids";
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_PAUSE = "pause";
    public static final String EXTRA_DISCARD = "discard";

    public static volatile boolean running;

    private final Map<String, DownloadTask> mTasks = new LinkedHashMap<>();
    private final Map<String, Download> mStates = new LinkedHashMap<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mNotifyRunnable = this::updateNotification;
    private ExecutorService mExecutor;
    private boolean mForeground;

    public static void start(Context context, List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_START);
        intent.putStringArrayListExtra(EXTRA_IDS, new ArrayList<>(ids));
        ContextCompat.startForegroundService(context, intent);
    }

    public static void cancel(String id, boolean pause) {
        Intent intent = new Intent(App.get(), DownloadService.class);
        intent.setAction(ACTION_STOP);
        intent.putExtra(EXTRA_ID, id);
        intent.putExtra(EXTRA_PAUSE, pause);
        try {
            App.get().startService(intent);
        } catch (Exception ignored) {
        }
    }

    public static void retry(String id) {
        Intent intent = new Intent(App.get(), DownloadService.class);
        intent.setAction(ACTION_RETRY);
        intent.putExtra(EXTRA_ID, id);
        try {
            App.get().startService(intent);
        } catch (Exception ignored) {
        }
    }

    public static void discard(String id) {
        Intent intent = new Intent(App.get(), DownloadService.class);
        intent.setAction(ACTION_STOP);
        intent.putExtra(EXTRA_ID, id);
        intent.putExtra(EXTRA_DISCARD, true);
        try {
            App.get().startService(intent);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        DownloadNotification.createChannel(this);
        mExecutor = Executors.newFixedThreadPool(2);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            stopIfEmpty();
            return START_NOT_STICKY;
        }
        if (!mForeground) {
            startForeground(DownloadNotification.NOTIFICATION_ID, DownloadNotification.build(this, "视频下载", "准备中…", 0, true));
            mForeground = true;
        }
        switch (intent.getAction()) {
            case ACTION_START:
                ArrayList<String> ids = intent.getStringArrayListExtra(EXTRA_IDS);
                if (ids != null) for (String id : ids) submit(id);
                break;
            case ACTION_STOP:
                String id = intent.getStringExtra(EXTRA_ID);
                if (id != null) {
                    if (intent.getBooleanExtra(EXTRA_DISCARD, false)) discardTask(id);
                    else stop(id, intent.getBooleanExtra(EXTRA_PAUSE, true));
                }
                break;
            case ACTION_RETRY:
                String retryId = intent.getStringExtra(EXTRA_ID);
                if (retryId != null) {
                    reset(retryId);
                    submit(retryId);
                }
                break;
        }
        updateNotification();
        return START_NOT_STICKY;
    }

    private void submit(String id) {
        if (id == null || mTasks.containsKey(id)) return;
        DownloadTask task = new DownloadTask(id, new DownloadTask.Callback() {
            @Override
            public void onStart(Download download) {
                if (download != null) mStates.put(id, download);
                scheduleNotify();
            }

            @Override
            public void onProgress(Download download) {
                if (download != null) mStates.put(id, download);
                scheduleNotify();
            }

            @Override
            public void onEnd(Download download) {
                mTasks.remove(id);
                mStates.remove(id);
                if (download != null && download.isCompleted()) {
                    mStates.put(id, download);
                    mHandler.postDelayed(() -> mStates.remove(id), 0);
                }
                if (mTasks.isEmpty()) mHandler.postDelayed(DownloadService.this::stopIfEmpty, 1200);
                else updateNotification();
            }
        });
        mTasks.put(id, task);
        mExecutor.execute(task);
    }

    private void discardTask(String id) {
        DownloadTask task = mTasks.remove(id);
        if (task != null) task.discard();
        mStates.remove(id);
        App.execute(() -> {
            Download download = Download.find(id);
            if (download != null) download.deleteVideoFile();
        });
        if (mTasks.isEmpty()) mHandler.postDelayed(this::stopIfEmpty, 500);
    }

    private void stop(String id, boolean pause) {
        DownloadTask task = mTasks.get(id);
        if (task != null) {
            task.cancel(pause);
            return;
        }
        // 任务不在运行：直接改数据库状态
        App.execute(() -> {
            Download download = AppDatabase.get().getDownloadDao().find(id);
            if (download == null) return;
            download.setStatusInt(pause ? Download.STATUS_PAUSED : Download.STATUS_FAILED);
            download.setStatus(pause ? "paused" : "failed");
            download.setSpeed(0);
            download.setError(pause ? "已暂停" : "已取消");
            download.save();
        });
    }

    private void reset(String id) {
        App.execute(() -> {
            Download download = AppDatabase.get().getDownloadDao().find(id);
            if (download == null) return;
            download.setStatusInt(Download.STATUS_PENDING);
            download.setStatus("pending");
            download.setProgress(0);
            download.setSpeed(0);
            download.setError("");
            download.save();
        });
    }

    private void scheduleNotify() {
        mHandler.removeCallbacks(mNotifyRunnable);
        mHandler.postDelayed(mNotifyRunnable, 500);
    }

    private void updateNotification() {
        if (!mForeground) return;
        if (mTasks.isEmpty() && mStates.isEmpty()) {
            stopIfEmpty();
            return;
        }
        int active = mTasks.size();
        Download current = mStates.values().stream().findFirst().orElse(null);
        String title = active > 0 ? "正在下载 " + active + " 项" : "下载完成";
        String text = "准备中…";
        int progress = 0;
        boolean indeterminate = true;
        if (current != null) {
            progress = current.getProgress();
            indeterminate = current.getTotalBytes() <= 0;
            StringBuilder sb = new StringBuilder(current.getDisplayName());
            sb.append("  ").append(String.format(Locale.getDefault(), "%d%%", progress));
            if (current.getSpeed() > 0) sb.append("  ").append(formatSpeed(current.getSpeed()));
            text = sb.toString();
        }
        try {
            NotificationManagerCompat.from(this).notify(DownloadNotification.NOTIFICATION_ID,
                    DownloadNotification.build(this, title, text, progress, indeterminate));
        } catch (SecurityException ignored) {
        }
    }

    private static String formatSpeed(long speed) {
        if (speed <= 0) return "";
        if (speed < 1024 * 1024) return String.format(Locale.ROOT, "%.0fKB/s", speed / 1024f);
        return String.format(Locale.ROOT, "%.1fMB/s", speed / 1024f / 1024f);
    }

    private void stopIfEmpty() {
        if (!mTasks.isEmpty()) return;
        if (mForeground) {
            stopForeground(true);
            mForeground = false;
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        running = false;
        mHandler.removeCallbacksAndMessages(null);
        if (mExecutor != null) mExecutor.shutdownNow();
        mTasks.clear();
        mStates.clear();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
