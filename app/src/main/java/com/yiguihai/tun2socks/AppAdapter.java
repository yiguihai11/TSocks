
package com.yiguihai.tun2socks;

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
        holder.appName.setText(appInfo.appName);
        holder.appIcon.setImageDrawable(appInfo.icon);
        holder.appSelected.setChecked(appInfo.isSelected);

        holder.itemView.setOnClickListener(v -> {
            holder.appSelected.toggle();
        });

        holder.appSelected.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appInfo.isSelected = isChecked;
            if (listener != null) {
                listener.onAppSelected(appInfo, isChecked);
            }
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName;
        CheckBox appSelected;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.image_view_app_icon);
            appName = itemView.findViewById(R.id.text_view_app_name);
            appSelected = itemView.findViewById(R.id.checkbox_app_selected);
        }
    }
}
