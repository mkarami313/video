package com.appoup.videotomp3.adapter;

import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.appoup.videotomp3.R;
import com.appoup.videotomp3.core.PlayerController;
import com.appoup.videotomp3.model.HistoryItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

    public interface Actions {
        void onPlayToggle(HistoryItem item);
        void onShare(HistoryItem item);
        void onSave(HistoryItem item);
        void onDelete(HistoryItem item);
        void onSeek(HistoryItem item, float fraction);
    }

    private final List<HistoryItem> items = new ArrayList<>();
    private final Actions actions;
    private final PlayerController player;

    public HistoryAdapter(Actions actions, PlayerController player) {
        this.actions = actions;
        this.player = player;
    }

    public void submit(List<HistoryItem> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        HistoryItem item = items.get(position);
        h.name.setText(item.name);
        String size = Formatter.formatShortFileSize(h.itemView.getContext(), item.sizeBytes);
        h.meta.setText(item.format.toUpperCase(Locale.US) + " · " + size
                + " · " + formatMs(item.durationMs));

        boolean isCurrent = item.id != null && item.id.equals(player.currentId());
        boolean playing = isCurrent && player.isPlaying();
        h.play.setIconResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        h.seek.setVisibility(isCurrent ? View.VISIBLE : View.GONE);
        if (isCurrent) {
            float f = clamp(player.fraction());
            h.seek.setValue(f);
        }

        h.play.setOnClickListener(x -> actions.onPlayToggle(item));
        h.share.setOnClickListener(x -> actions.onShare(item));
        h.save.setOnClickListener(x -> actions.onSave(item));
        h.delete.setOnClickListener(x -> actions.onDelete(item));
        h.seek.clearOnChangeListeners();
        h.seek.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) actions.onSeek(item, value);
        });
    }

    private static float clamp(float f) {
        if (f < 0) return 0; if (f > 1) return 1; return f;
    }

    private static String formatMs(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, meta;
        MaterialButton play, share, save, delete;
        Slider seek;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.txtName);
            meta = v.findViewById(R.id.txtMeta);
            play = v.findViewById(R.id.btnPlay);
            share = v.findViewById(R.id.btnShare);
            save = v.findViewById(R.id.btnSave);
            delete = v.findViewById(R.id.btnDelete);
            seek = v.findViewById(R.id.seek);
        }
    }
}
