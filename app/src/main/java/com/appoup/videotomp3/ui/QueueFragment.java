package com.appoup.videotomp3.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.appoup.videotomp3.R;
import com.appoup.videotomp3.adapter.QueueAdapter;
import com.appoup.videotomp3.core.ConversionManager;
import com.appoup.videotomp3.model.Job;

public class QueueFragment extends Fragment implements ConversionManager.Listener {

    private QueueAdapter adapter;
    private TextView txtEmpty;
    private ConversionManager manager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_queue, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        manager = ConversionManager.get(requireContext());
        txtEmpty = v.findViewById(R.id.txtEmpty);
        RecyclerView rv = v.findViewById(R.id.recyclerQueue);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new QueueAdapter(job -> manager.cancel(job));
        rv.setAdapter(adapter);

        v.findViewById(R.id.btnClear).setOnClickListener(x -> manager.clearFinished());
        refresh();
    }

    private void refresh() {
        java.util.List<Job> jobs = manager.getJobs();
        adapter.submit(jobs);
        txtEmpty.setVisibility(jobs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        manager.addListener(this);
        refresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        manager.removeListener(this);
    }

    @Override
    public void onQueueChanged() { refresh(); }

    @Override
    public void onJobProgress(Job job) { refresh(); }
}
