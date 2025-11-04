
package com.yiguihai.tun2socks;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import android.text.Editable;
import android.text.TextWatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppSelectionActivity extends AppCompatActivity {

    private static final String TAG = "AppSelectionActivity";
    public static final String PREF_SELECTED_APPS = "pref_selected_apps";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private AppAdapter appAdapter;
    private List<AppInfo> appList = new ArrayList<>();
    private List<AppInfo> filteredAppList = new ArrayList<>();

    // UI components
    private TextInputEditText searchEditText;
    private ChipGroup chipGroupFilter;
    private Chip chipAllApps, chipUserApps, chipSystemApps;
    private TextView statsTextView;

    // Filter state
    private String currentFilter = "all";
    private String searchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_selection);

        // Initialize UI components
        recyclerView = findViewById(R.id.recycler_view_apps);
        progressBar = findViewById(R.id.progress_bar_loading_apps);
        searchEditText = findViewById(R.id.edit_text_search);
        chipGroupFilter = findViewById(R.id.chip_group_filter);
        statsTextView = findViewById(R.id.text_stats);

        chipAllApps = findViewById(R.id.chip_all_apps);
        chipUserApps = findViewById(R.id.chip_user_apps);
        chipSystemApps = findViewById(R.id.chip_system_apps);

        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        appAdapter = new AppAdapter(filteredAppList);
        appAdapter.setOnAppSelectedListener(this::onAppSelected); // Set up real-time save listener
        recyclerView.setAdapter(appAdapter);

        // Set up listeners
        setupSearchListener();
        setupFilterListeners();

        // Initially select "All Apps" chip
        chipAllApps.setChecked(true);

        loadApps();
    }

    private void loadApps() {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            // 使用getInstalledPackages获取包含权限信息的包列表，适配 API 33+
            List<PackageInfo> packages;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
                packages = pm.getInstalledPackages(PackageManager.PackageInfoFlags.of((long) PackageManager.GET_PERMISSIONS));
                Log.i(TAG, "使用新版 PackageManager API (API 33+) 获取应用列表");
            } else {
                packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
                Log.i(TAG, "使用旧版 PackageManager API 获取应用列表");
            }

            
            Set<String> selectedApps = PreferenceManager.getDefaultSharedPreferences(this)
                    .getStringSet(PREF_SELECTED_APPS, new HashSet<>());

            // Get current package name to exclude ourselves
            String currentPackageName = getPackageName();

            for (PackageInfo packageInfo : packages) {
                String packageName = packageInfo.packageName;

                // Only exclude our own package
                if (packageName.equals(currentPackageName)) {
                    continue;
                }

                // Check if app has internet permission - only show apps that can actually use network
                if (hasInternetPermission(packageInfo)) {
                    ApplicationInfo appInfo = packageInfo.applicationInfo;
                    // Filter out disabled applications
                    if (!appInfo.enabled) {
                        continue;
                    }
                    String appName = appInfo.loadLabel(pm).toString();
                    boolean isSelected = selectedApps.contains(packageName);
                    boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

                    try {
                        appList.add(new AppInfo(appName, packageName, appInfo.loadIcon(pm), isSelected, isSystemApp, appInfo.uid));
                    } catch (Exception e) {
                        // 记录图标加载失败的情况，但不跳过应用（使用默认图标）
                        Log.w(TAG, "应用 " + packageName + " 图标加载失败: " + e.getMessage());
                        try {
                            // 尝试使用默认图标
                            appList.add(new AppInfo(appName, packageName, null, isSelected, isSystemApp, appInfo.uid));
                        } catch (Exception fallbackException) {
                            Log.e(TAG, "应用 " + packageName + " 完全添加失败: " + fallbackException.getMessage());
                        }
                    }
                }
            }

            // Sort apps: enabled apps first, then by name
            Collections.sort(appList, (o1, o2) -> {
                if (o1.isSelected != o2.isSelected) {
                    return o2.isSelected ? 1 : -1; // enabled apps first
                }
                return o1.appName.compareToIgnoreCase(o2.appName); // then by name
            });

            
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                updateFilteredApps();
                updateStats();
            });
        }).start();
    }

    private boolean hasInternetPermission(PackageInfo packageInfo) {
        // 检查应用是否有INTERNET权限
        if (packageInfo.requestedPermissions != null) {
            for (String permission : packageInfo.requestedPermissions) {
                if (Manifest.permission.INTERNET.equals(permission)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Real-time save when app is selected/deselected
    private void onAppSelected(AppInfo app, boolean isSelected) {
        saveAppSelection();
        updateStats(); // Update statistics immediately
    }

    private void saveAppSelection() {
        Set<String> selectedApps = new HashSet<>();
        for (AppInfo appInfo : appList) {
            if (appInfo.isSelected) {
                selectedApps.add(appInfo.packageName);
            }
        }

        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putStringSet(PREF_SELECTED_APPS, selectedApps);
        editor.apply();
    }

    
    private void setupSearchListener() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().toLowerCase();
                updateFilteredApps();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFilterListeners() {
        // Set individual click listeners for each chip to ensure they work
        chipAllApps.setOnClickListener(v -> {
            currentFilter = "all";
            chipAllApps.setChecked(true);
            chipUserApps.setChecked(false);
            chipSystemApps.setChecked(false);
            updateFilteredApps();
        });

        chipUserApps.setOnClickListener(v -> {
            currentFilter = "user";
            chipAllApps.setChecked(false);
            chipUserApps.setChecked(true);
            chipSystemApps.setChecked(false);
            updateFilteredApps();
        });

        chipSystemApps.setOnClickListener(v -> {
            currentFilter = "system";
            chipAllApps.setChecked(false);
            chipUserApps.setChecked(false);
            chipSystemApps.setChecked(true);
            updateFilteredApps();
        });
    }

    private void updateFilteredApps() {
        filteredAppList.clear();

        for (AppInfo app : appList) {
            // Apply filter
            boolean matchesFilter = true;
            if (currentFilter.equals("user") && app.isSystemApp) {
                matchesFilter = false;
            } else if (currentFilter.equals("system") && !app.isSystemApp) {
                matchesFilter = false;
            }

            // Apply search
            boolean matchesSearch = searchQuery.isEmpty() ||
                    app.appName.toLowerCase().contains(searchQuery) ||
                    app.packageName.toLowerCase().contains(searchQuery);

            if (matchesFilter && matchesSearch) {
                filteredAppList.add(app);
            }
        }

        // Use notifyDataSetChanged() for simple refresh, or consider better diff utils
        runOnUiThread(() -> {
            appAdapter.notifyDataSetChanged();
            updateStats();
        });
    }

    private void updateStats() {
        int totalCount = appList.size();
        int filteredCount = filteredAppList.size();
        int enabledCount = 0;

        for (AppInfo app : appList) {
            if (app.isSelected) enabledCount++;
        }

        String filterText = "";
        switch (currentFilter) {
            case "user":
                filterText = "User Apps";
                break;
            case "system":
                filterText = "System Apps";
                break;
            default:
                filterText = "All Apps";
        }

        String searchText = searchQuery.isEmpty() ? "" : " (search: " + searchQuery + ")";

        statsTextView.setText(String.format("Total: %d | Showing: %d%s | Selected: %d | Filter: %s",
                totalCount, filteredCount, searchText, enabledCount, filterText));
    }
}
