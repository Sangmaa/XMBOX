package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.DialogDownloadEpisodeBinding;
import com.fongmi.android.tv.databinding.ItemDownloadEpisodeBinding;
import com.fongmi.android.tv.ui.activity.DownloadActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 选集下载弹窗：多选剧集后批量加入下载。
 */
public class DownloadEpisodeDialog extends BaseDialog {

    public interface Callback {
        void onDownload(List<Episode> episodes);
    }

    private DialogDownloadEpisodeBinding binding;
    private List<Episode> episodes = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private Callback callback;
    private boolean reverse;

    public static DownloadEpisodeDialog create() {
        return new DownloadEpisodeDialog();
    }

    public DownloadEpisodeDialog episodes(List<Episode> episodes) {
        this.episodes = episodes == null ? new ArrayList<>() : new ArrayList<>(episodes);
        return this;
    }

    public DownloadEpisodeDialog reverse(boolean reverse) {
        this.reverse = reverse;
        return this;
    }

    public DownloadEpisodeDialog callback(Callback callback) {
        this.callback = callback;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDownloadEpisodeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        if (episodes.isEmpty()) {
            dismiss();
            return;
        }
        if (reverse) Collections.reverse(episodes);
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(new Adapter());
        binding.list.setOnClickListener(v -> {
            DownloadActivity.start(requireActivity());
            dismiss();
        });
        binding.toggle.setOnClickListener(v -> toggleAll());
        updateStart();
    }

    @Override
    protected void initEvent() {
        binding.start.setOnClickListener(v -> {
            List<Episode> chosen = new ArrayList<>();
            for (Episode episode : episodes) if (selected.contains(episode.getUrl())) chosen.add(episode);
            if (chosen.isEmpty()) return;
            dismiss();
            if (callback != null) callback.onDownload(chosen);
        });
    }

    private void toggleAll() {
        boolean all = selected.size() == episodes.size();
        selected.clear();
        if (!all) for (Episode episode : episodes) selected.add(episode.getUrl());
        binding.recycler.getAdapter().notifyDataSetChanged();
        updateStart();
    }

    private void updateStart() {
        int count = selected.size();
        binding.toggle.setText(selected.size() == episodes.size() ? R.string.download_batch_none : R.string.download_batch_all);
        String text = getString(R.string.download_batch_start);
        binding.start.setText(count == 0 ? text : text + " (" + count + ")");
        binding.start.setEnabled(count > 0);
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemDownloadEpisodeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(episodes.get(position));
        }

        @Override
        public int getItemCount() {
            return episodes.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            private final ItemDownloadEpisodeBinding item;

            ViewHolder(ItemDownloadEpisodeBinding item) {
                super(item.getRoot());
                this.item = item;
            }

            void bind(Episode episode) {
                String name = episode.getName().isEmpty() ? episode.getUrl() : episode.getName();
                item.text.setText(name);
                item.check.setChecked(selected.contains(episode.getUrl()));
                item.getRoot().setOnClickListener(v -> {
                    if (!selected.remove(episode.getUrl())) selected.add(episode.getUrl());
                    notifyItemChanged(getBindingAdapterPosition());
                    updateStart();
                });
                item.check.setOnClickListener(v -> item.getRoot().performClick());
            }
        }
    }
}
