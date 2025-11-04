
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
        recyclerView.setAdapter(appAdapter);

        // Set up listeners
        saveButton.setOnClickListener(v -> saveSelection());
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
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            Set<String> selectedApps = PreferenceManager.getDefaultSharedPreferences(this)
                    .getStringSet(PREF_SELECTED_APPS, new HashSet<>());

            // Get current package name to exclude ourselves
            String currentPackageName = getPackageName();

            for (ApplicationInfo packageInfo : packages) {
                String packageName = packageInfo.packageName;

                // Only exclude our own package
                if (packageName.equals(currentPackageName)) {
                    continue;
                }

                // Check if app has internet permission (like your Compose code)
                if (hasInternetPermission(pm, packageName)) {
                    String appName = packageInfo.loadLabel(pm).toString();
                    boolean isSelected = selectedApps.contains(packageName);
                    boolean isSystemApp = (packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

                    appList.add(new AppInfo(appName, packageName, packageInfo.loadIcon(pm), isSelected, isSystemApp));
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

    private boolean hasInternetPermission(PackageManager pm, String packageName) {
        try {
            PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            if (packageInfo.requestedPermissions != null) {
                for (String permission : packageInfo.requestedPermissions) {
                    if (Manifest.permission.INTERNET.equals(permission)) {
                        return true;
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            // Package not found, skip it
        }
        return false;
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

        appAdapter.notifyDataSetChanged();
        updateStats();
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

    private void saveSelection() {
        Set<String> selectedApps = new HashSet<>();
        for (AppInfo appInfo : appList) {
            if (appInfo.isSelected) {
                selectedApps.add(appInfo.packageName);
            }
        }

        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putStringSet(PREF_SELECTED_APPS, selectedApps);
        editor.apply();

        Toast.makeText(this, "Selection saved (" + selectedApps.size() + " apps)", Toast.LENGTH_SHORT).show();
        finish(); // Close the activity
    }
}
