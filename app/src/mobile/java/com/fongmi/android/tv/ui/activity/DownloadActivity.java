package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.databinding.ActivityDownloadBinding;
import com.fongmi.android.tv.download.DownloadStorage;
import com.fongmi.android.tv.service.DownloadService;
import com.fongmi.android.tv.ui.adapter.DownloadAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DownloadActivity extends BaseActivity implements DownloadAdapter.OnClickListener {

    private static final long REFRESH_INTERVAL = 1000;

    private ActivityDownloadBinding mBinding;
    private DownloadAdapter mAdapter;
    private boolean mAskedPermission;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRefresh = this::refresh;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, DownloadActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityDownloadBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setRecyclerView();
    }

    @Override
    protected void initEvent() {
        mBinding.back.setOnClickListener(v -> finish());
        mBinding.delete.setOnClickListener(this::onDeleteAll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Android 10 及以下需要写外部存储权限才能落到公共 Download/XMBOX；更高版本直接回退私有目录
        if (!mAskedPermission && Build.VERSION.SDK_INT < 30 && !DownloadStorage.isPublicWritable()) {
            mAskedPermission = true;
            PermissionUtil.requestFile(this, granted -> DownloadStorage.resetWritableCache());
        }
        mHandler.post(mRefresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mRefresh);
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setLayoutManager(new LinearLayoutManager(this));
        mBinding.recycler.setAdapter(mAdapter = new DownloadAdapter(this));
    }

    private void refresh() {
        App.execute(() -> {
            List<Download> downloads = new ArrayList<>(Download.getAll());
            if (!DownloadService.running) {
                // 进程被杀后残留的“下载中”记录改回暂停，用户点继续即可续传
                for (Download download : downloads) {
                    if (download.getStatusInt() != Download.STATUS_DOWNLOADING) continue;
                    download.setStatusInt(Download.STATUS_PAUSED);
                    download.setStatus("paused");
                    download.setSpeed(0);
                    download.setError("已暂停");
                    download.save();
                }
            }
            for (Download download : downloads) {
                if (!download.isCompleted() || download.isValidFile()) continue;
                download.setStatusInt(Download.STATUS_FAILED);
                download.setStatus("failed");
                download.setError(getString(R.string.download_invalid_file));
                download.save();
            }
            Collections.sort(downloads, (a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()));
            App.post(() -> {
                mAdapter.submitList(downloads);
                updateEmptyState();
                mHandler.postDelayed(mRefresh, REFRESH_INTERVAL);
            });
        });
    }

    private void updateEmptyState() {
        boolean isEmpty = mAdapter.getItemCount() == 0;
        mBinding.emptyLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        mBinding.recycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        mBinding.delete.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void onDeleteAll(View view) {
        if (mAdapter.getItemCount() == 0) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_record)
                .setMessage(R.string.dialog_delete_download)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> deleteAll())
                .show();
    }

    private void deleteAll() {
        List<Download> downloads = new ArrayList<>(mAdapter.getCurrentList());
        App.execute(() -> {
            for (Download download : downloads) DownloadService.discard(download.getId());
            Download.clear();
            App.post(this::refresh);
        });
    }

    private void delete(Download item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_record)
                .setMessage(getString(R.string.download_delete_confirm, item.getDisplayName()))
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> App.execute(() -> {
                    DownloadService.discard(item.getId());
                    item.delete();
                    App.post(this::refresh);
                }))
                .show();
    }

    @Override
    public void onItemClick(Download item) {
        if (item.isCompleted() && item.fileExists()) {
            VideoActivity.start(this, "push_agent", "file://" + item.getLocalPath(), item.getDisplayName());
        } else if (item.isFailed() || item.isPaused()) {
            DownloadService.retry(item.getId());
            Notify.show(R.string.download_pending);
        } else if (item.isCompleted()) {
            Notify.show(R.string.download_file_missing);
        }
    }

    @Override
    public void onItemDelete(Download item) {
        delete(item);
    }

    @Override
    public void onActionClick(Download item) {
        if (item.isCompleted()) {
            delete(item);
        } else if (item.isDownloading() || !item.isFailed() && !item.isPaused()) {
            DownloadService.cancel(item.getId(), true);
            Notify.show(R.string.download_paused);
        } else {
            DownloadService.retry(item.getId());
            Notify.show(R.string.download_pending);
        }
    }
}
