package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.databinding.AdapterDownloadBinding;
import com.fongmi.android.tv.utils.UrlUtil;

import java.util.Locale;

public class DownloadAdapter extends ListAdapter<Download, DownloadAdapter.ViewHolder> {

    private final OnClickListener listener;

    public interface OnClickListener {
        void onItemClick(Download item);

        void onActionClick(Download item);

        void onItemDelete(Download item);
    }

    private static final DiffUtil.ItemCallback<Download> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Download oldItem, @NonNull Download newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Download oldItem, @NonNull Download newItem) {
            return oldItem.getProgress() == newItem.getProgress()
                    && oldItem.getStatusInt() == newItem.getStatusInt()
                    && oldItem.getSpeed() == newItem.getSpeed()
                    && oldItem.getTotalBytes() == newItem.getTotalBytes()
                    && oldItem.getDownloaded() == newItem.getDownloaded()
                    && oldItem.getEpisodeName().equals(newItem.getEpisodeName())
                    && oldItem.getVodName().equals(newItem.getVodName())
                    && oldItem.getVodPic().equals(newItem.getVodPic())
                    && oldItem.getError().equals(newItem.getError());
        }
    };

    public DownloadAdapter(OnClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterDownloadBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.initView(getItem(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterDownloadBinding binding;

        ViewHolder(AdapterDownloadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void initView(Download item) {
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onItemDelete(item);
                return true;
            });
            binding.name.setText(item.getDisplayName());
            Glide.with(App.get()).load(UrlUtil.convert(item.getVodPic())).placeholder(R.drawable.ic_nav_vod).into(binding.cover);
            int status = item.getStatusInt();
            if (status == Download.STATUS_COMPLETED) {
                binding.status.setText(R.string.download_completed);
                binding.progress.setProgress(100);
                binding.progressText.setText(formatSize(item.getTotalBytes()));
                binding.action.setImageResource(R.drawable.ic_action_delete);
            } else if (status == Download.STATUS_PAUSED) {
                binding.status.setText(getString(R.string.download_paused) + "  " + item.getProgress() + "%");
                binding.progress.setProgress(item.getProgress());
                String sub = downloadedText(item);
                binding.progressText.setVisibility(sub.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
                binding.progressText.setText(sub);
                binding.action.setImageResource(R.drawable.ic_notify_play);
            } else if (status == Download.STATUS_FAILED) {
                binding.status.setText(item.getError().isEmpty() ? getString(R.string.download_failed) : item.getError());
                binding.progress.setProgress(item.getProgress());
                String sub = downloadedText(item);
                binding.progressText.setVisibility(sub.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
                binding.progressText.setText(sub);
                binding.action.setImageResource(R.drawable.ic_action_retry);
            } else if (status == Download.STATUS_DOWNLOADING) {
                binding.status.setText(activeText(item));
                binding.progress.setProgress(item.getProgress());
                String sub = downloadedText(item);
                binding.progressText.setVisibility(sub.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
                binding.progressText.setText(sub);
                binding.action.setImageResource(R.drawable.ic_notify_pause);
            } else {
                binding.status.setText(R.string.download_pending);
                binding.progress.setProgress(0);
                binding.progressText.setText(R.string.download_pending);
                binding.action.setImageResource(R.drawable.ic_notify_pause);
            }
            binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
            binding.action.setOnClickListener(v -> listener.onActionClick(item));
        }

        private String activeText(Download item) {
            return getString(R.string.downloading) + "  " + item.getProgress() + '%';
        }

        private String downloadedText(Download item) {
            StringBuilder sb = new StringBuilder();
            long done = item.getDownloaded();
            if (done > 0) sb.append(getString(R.string.download_downloaded_only, formatSize(done)));
            if (done > 0 && item.getTotalBytes() > 0) sb.append(" / ").append(formatSize(item.getTotalBytes()));
            if (item.getSpeed() > 0) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(formatSpeed(item.getSpeed()));
            }
            return sb.toString();
        }

        private String sizeText(Download item) {
            long total = item.getTotalBytes();
            if (total <= 0) return String.format(Locale.getDefault(), "%d%%", item.getProgress());
            long downloaded = item.getProgress() >= 100 ? total : total * item.getProgress() / 100;
            return binding.getRoot().getContext().getString(R.string.download_downloaded, formatSize(downloaded), formatSize(total));
        }

        private String getString(int res) {
            return binding.getRoot().getContext().getString(res);
        }

        private String getString(int res, Object... args) {
            return binding.getRoot().getContext().getString(res, args);
        }

        private String formatSize(long bytes) {
            if (bytes <= 0) return "";
            if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.0fKB", bytes / 1024f);
            if (bytes < 1024L * 1024 * 1024) return String.format(Locale.getDefault(), "%.1fMB", bytes / 1024f / 1024f);
            return String.format(Locale.getDefault(), "%.2fGB", bytes / 1024f / 1024f / 1024f);
        }

        private String formatSpeed(long speed) {
            if (speed <= 0) return "";
            if (speed < 1024 * 1024) return String.format(Locale.getDefault(), "%.0fKB/s", speed / 1024f);
            return String.format(Locale.getDefault(), "%.1fMB/s", speed / 1024f / 1024f);
        }
    }
}
