package com.appoup.videotomp3.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.appoup.videotomp3.R;
import com.appoup.videotomp3.adapter.HistoryAdapter;
import com.appoup.videotomp3.core.ConversionManager;
import com.appoup.videotomp3.core.HistoryStore;
import com.appoup.videotomp3.core.MediaStoreSaver;
import com.appoup.videotomp3.core.PlayerController;
import com.appoup.videotomp3.model.HistoryItem;
import com.appoup.videotomp3.model.Job;
import com.appoup.videotomp3.model.OutputFormat;

import java.io.File;
import java.util.List;

public class LibraryFragment extends Fragment
        implements HistoryAdapter.Actions, ConversionManager.Listener {

    private HistoryStore store;
    private HistoryAdapter adapter;
    private TextView txtEmpty;
    private final PlayerController player = new PlayerController();
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private Runnable tick;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        store = new HistoryStore(requireContext());
        txtEmpty = v.findViewById(R.id.txtEmpty);
        RecyclerView rv = v.findViewById(R.id.recyclerLibrary);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new HistoryAdapter(this, player);
        rv.setAdapter(adapter);

        player.setListener(() -> adapter.notifyDataSetChanged());

        tick = new Runnable() {
            @Override public void run() {
                if (player.isPlaying()) adapter.notifyDataSetChanged();
                ticker.postDelayed(this, 500);
            }
        };
        refresh();
    }

    private void refresh() {
        List<HistoryItem> list = store.load();
        adapter.submit(list);
        txtEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        ConversionManager.get(requireContext()).addListener(this);
        ticker.postDelayed(tick, 500);
        refresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        ConversionManager.get(requireContext()).removeListener(this);
        ticker.removeCallbacks(tick);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        player.stop();
    }

    // ---- adapter actions ----
    @Override
    public void onPlayToggle(HistoryItem item) {
        if (!new File(item.path).exists()) {
            toast("File missing");
            refreshRemoved(item);
            return;
        }
        player.toggle(item.id, item.path);
    }

    @Override
    public void onShare(HistoryItem item) {
        File f = new File(item.path);
        if (!f.exists()) { toast("File missing"); return; }
        Uri uri = FileProvider.getUriForFile(requireContext(),
                requireContext().getPackageName() + ".fileprovider", f);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(mimeFor(item.format));
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, getString(R.string.share)));
    }

    @Override
    public void onSave(HistoryItem item) {
        File f = new File(item.path);
        boolean ok = MediaStoreSaver.saveToMusic(requireContext(), f, mimeFor(item.format));
        toast(getString(ok ? R.string.saved_music : R.string.save_failed));
    }

    @Override
    public void onDelete(HistoryItem item) {
        if (item.id != null && item.id.equals(player.currentId())) player.stop();
        File f = new File(item.path);
        if (f.exists()) f.delete();
        store.remove(item.id);
        toast(getString(R.string.deleted));
        refresh();
    }

    @Override
    public void onSeek(HistoryItem item, float fraction) {
        if (item.id != null && item.id.equals(player.currentId())) {
            player.seekToFraction(fraction);
        }
    }

    private void refreshRemoved(HistoryItem item) {
        store.remove(item.id);
        refresh();
    }

    private static String mimeFor(String ext) {
        return OutputFormat.fromName(ext).mime;
    }

    private void toast(String s) {
        Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onQueueChanged() { refresh(); }

    @Override
    public void onJobProgress(Job job) { }
}
