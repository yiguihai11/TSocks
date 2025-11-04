
package com.yiguihai.tun2socks;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
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
        Button saveButton = findViewById(R.id.button_save_app_selection);
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
        saveButton.setOnClickListener(v -> showSelectedAppsInfo()); // Change save function
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
            // 使用getInstalledPackages获取包含权限信息的包列表
            List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);

            // 立即检查 com.termux 是否在原始包列表中（过滤前）
            boolean termuxExistsInPackages = false;
            PackageInfo termuxPackageInfo = null;
            for (PackageInfo pkg : packages) {
                if ("com.termux".equals(pkg.packageName)) {
                    termuxExistsInPackages = true;
                    termuxPackageInfo = pkg;
                    break;
                }
            }

            final boolean foundInOriginalList = termuxExistsInPackages;
            final PackageInfo foundPackageInfo = termuxPackageInfo;

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

                // Show all apps regardless of network permission
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
                // 如果图标加载失败，跳过此应用
                continue;
            }
            }

            // Sort apps: enabled apps first, then by name
            Collections.sort(appList, (o1, o2) -> {
                if (o1.isSelected != o2.isSelected) {
                    return o2.isSelected ? 1 : -1; // enabled apps first
                }
                return o1.appName.compareToIgnoreCase(o2.appName); // then by name
            });

            // 查找 com.termux 包名
            AppInfo termuxApp = null;
            List<AppInfo> termuxRelatedApps = new ArrayList<>();
            for (AppInfo app : appList) {
                if ("com.termux".equals(app.packageName)) {
                    termuxApp = app;
                }
                // 查找所有包含 "termux" 的应用
                if (app.packageName.toLowerCase().contains("termux") ||
                    app.appName.toLowerCase().contains("termux")) {
                    termuxRelatedApps.add(app);
                }
            }

            final AppInfo foundTermuxApp = termuxApp;

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                updateFilteredApps();
                updateStats();

                // 显示查找结果
                String resultMessage = "";
                if (foundInOriginalList) {
                    resultMessage = "✓ 系统包列表中找到 com.termux\n";
                    if (foundPackageInfo != null && foundPackageInfo.applicationInfo != null) {
                        boolean isEnabled = foundPackageInfo.applicationInfo.enabled;
                        resultMessage += "应用状态: " + (isEnabled ? "已启用" : "已禁用") + "\n";
                        boolean isSystemApp = (foundPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        resultMessage += "应用类型: " + (isSystemApp ? "系统应用" : "用户应用") + "\n";
                        resultMessage += "UID: " + foundPackageInfo.applicationInfo.uid;
                    }
                } else {
                    resultMessage = "✗ 系统包列表中没有 com.termux\n";
                    resultMessage += "说明应用未安装或系统无法识别";
                }

                if (foundTermuxApp != null) {
                    resultMessage += "\n\n✓ 在应用列表中找到: " + foundTermuxApp.appName;
                } else if (foundInOriginalList) {
                    resultMessage += "\n\n⚠ 在包列表中找到但被过滤掉了";
                }

                Toast.makeText(AppSelectionActivity.this, resultMessage, Toast.LENGTH_LONG).show();
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
        chipGroupFilter.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chip_all_apps) {
                currentFilter = "all";
            } else if (checkedId == R.id.chip_user_apps) {
                currentFilter = "user";
            } else if (checkedId == R.id.chip_system_apps) {
                currentFilter = "system";
            }
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
        boolean hasTermux = false;

        for (AppInfo app : appList) {
            if (app.isSelected) enabledCount++;
            if ("com.termux".equals(app.packageName)) {
                hasTermux = true;
            }
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
        String termuxStatus = hasTermux ? " | Termux: ✓" : " | Termux: ✗";

        statsTextView.setText(String.format("Total: %d | Showing: %d%s | Selected: %d | Filter: %s%s",
                totalCount, filteredCount, searchText, enabledCount, filterText, termuxStatus));
    }

    private void showSelectedAppsInfo() {
        Set<String> selectedApps = new HashSet<>();
        for (AppInfo appInfo : appList) {
            if (appInfo.isSelected) {
                selectedApps.add(appInfo.packageName);
            }
        }

        if (selectedApps.isEmpty()) {
            Toast.makeText(this, "No apps selected for VPN", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, selectedApps.size() + " apps selected for VPN", Toast.LENGTH_SHORT).show();
        }
    }
}
