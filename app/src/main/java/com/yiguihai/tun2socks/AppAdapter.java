
package com.yiguihai.tun2socks;

import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.AppViewHolder> {

    private final List<AppInfo> appList;
    private OnAppSelectedListener listener;

    public interface OnAppSelectedListener {
        void onAppSelected(AppInfo app, boolean isSelected);
    }

    public AppAdapter(List<AppInfo> appList) {
        this.appList = appList;
    }

    public void setOnAppSelectedListener(OnAppSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppInfo appInfo = appList.get(position);

        // 设置序号（从1开始计数）
        holder.appNumber.setText((position + 1) + ".");

        holder.appName.setText(appInfo.appName);
        // 处理可能的 null 图标
        if (appInfo.icon != null) {
            holder.appIcon.setImageDrawable(appInfo.icon);
        } else {
            // 使用系统默认应用图标
            holder.appIcon.setImageDrawable(holder.itemView.getContext().getPackageManager().getDefaultActivityIcon());
        }

        // Remove previous listener to avoid unwanted triggers
        holder.appSelected.setOnCheckedChangeListener(null);
        // Set checked state
        holder.appSelected.setChecked(appInfo.isSelected);

        // Set app details text
        String details = String.format("%s | UID: %d | %s",
                appInfo.packageName,
                appInfo.uid,
                appInfo.isSystemApp ? "系统应用" : "用户应用");
        holder.appDetails.setText(details);

        holder.itemView.setOnClickListener(v -> {
            // Toggle the selection state
            boolean newState = !appInfo.isSelected;
            appInfo.isSelected = newState;
            holder.appSelected.setChecked(newState);

            if (listener != null) {
                listener.onAppSelected(appInfo, newState);
            }
        });

        // Set new listener after the checked state is set
        holder.appSelected.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Update the model only if this is not just a view recycling
            if (appInfo.isSelected != isChecked) {
                appInfo.isSelected = isChecked;
                if (listener != null) {
                    listener.onAppSelected(appInfo, isChecked);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        TextView appNumber; // 序号显示
        ImageView appIcon;
        TextView appName;
        TextView appDetails; // Added
        CheckBox appSelected;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            appNumber = itemView.findViewById(R.id.text_view_app_number); // Initialized
            appIcon = itemView.findViewById(R.id.image_view_app_icon);
            appName = itemView.findViewById(R.id.text_view_app_name);
            appDetails = itemView.findViewById(R.id.text_view_app_details); // Initialized
            appSelected = itemView.findViewById(R.id.checkbox_app_selected);
        }
    }
}
