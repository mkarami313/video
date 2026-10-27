package com.appoup.videotomp3.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.appoup.videotomp3.R;
import com.appoup.videotomp3.model.Job;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.VH> {

    public interface OnCancel { void cancel(Job job); }

    private final List<Job> items = new ArrayList<>();
    private final OnCancel onCancel;

    public QueueAdapter(OnCancel onCancel) { this.onCancel = onCancel; }

    public void submit(List<Job> jobs) {
        items.clear();
        items.addAll(jobs);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_queue, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Job job = items.get(position);
        h.name.setText(job.displayName());
        h.state.setText(stateText(h.itemView, job));
        boolean active = job.state == Job.State.RUNNING || job.state == Job.State.QUEUED;
        h.progress.setVisibility(job.state == Job.State.RUNNING ? View.VISIBLE : View.GONE);
        if (job.state == Job.State.RUNNING) {
            h.progress.setIndeterminate(job.progress <= 0);
            if (job.progress > 0) h.progress.setProgress(job.progress);
        }
        h.cancel.setVisibility(active ? View.VISIBLE : View.GONE);
        h.cancel.setOnClickListener(x -> onCancel.cancel(job));
    }

    private String stateText(View v, Job job) {
        switch (job.state) {
            case RUNNING: return v.getContext().getString(R.string.state_running)
                    + (job.progress > 0 ? " " + job.progress + "%" : "");
            case DONE: return v.getContext().getString(R.string.state_done);
            case FAILED: return v.getContext().getString(R.string.state_failed)
                    + (job.error != null ? ": " + job.error : "");
            case CANCELLED: return v.getContext().getString(R.string.state_cancelled);
            default: return v.getContext().getString(R.string.state_queued);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, state;
        LinearProgressIndicator progress;
        MaterialButton cancel;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.txtName);
            state = v.findViewById(R.id.txtState);
            progress = v.findViewById(R.id.progress);
            cancel = v.findViewById(R.id.btnCancel);
        }
    }
}
