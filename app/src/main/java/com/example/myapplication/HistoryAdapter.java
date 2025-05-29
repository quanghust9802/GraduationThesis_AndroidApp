package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private List<AccessLog> accessLogs;

    public HistoryAdapter(List<AccessLog> accessLogs) {
        this.accessLogs = accessLogs;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_access_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AccessLog log = accessLogs.get(position);
        String fullName = log.getUser() != null && log.getUser().getFullName() != null
                ? log.getUser().getFullName() : "Không xác định";
        holder.cccdTextView.setText("Người dùng: " + fullName);

        // Định dạng accessTime
        String accessTime = log.getAccessTime() != null ? log.getAccessTime() : "N/A";
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            Date date = inputFormat.parse(accessTime);
            holder.timeTextView.setText("Thời gian: " + outputFormat.format(date));
        } catch (Exception e) {
            holder.timeTextView.setText("Thời gian: " + accessTime);
        }

        holder.statusTextView.setText("Trạng thái: " + (log.getStatus() != null && log.getStatus() == 1 ? "Cho phép" : "Từ chối"));
    }

    @Override
    public int getItemCount() {
        return accessLogs.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView cccdTextView, timeTextView, statusTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cccdTextView = itemView.findViewById(R.id.cccdTextView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
            statusTextView = itemView.findViewById(R.id.statusTextView);
        }
    }
}