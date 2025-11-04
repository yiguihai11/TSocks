
package com.yiguihai.tun2socks;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_selection);

        recyclerView = findViewById(R.id.recycler_view_apps);
        progressBar = findViewById(R.id.progress_bar_loading_apps);
        Button saveButton = findViewById(R.id.button_save_app_selection);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        appAdapter = new AppAdapter(appList);
        recyclerView.setAdapter(appAdapter);

        saveButton.setOnClickListener(v -> saveSelection());

        loadApps();
    }

    private void loadApps() {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            Set<String> selectedApps = PreferenceManager.getDefaultSharedPreferences(this)
                    .getStringSet(PREF_SELECTED_APPS, new HashSet<>());

            for (ApplicationInfo packageInfo : packages) {
                // Include both user and system apps
                String appName = packageInfo.loadLabel(pm).toString();
                String packageName = packageInfo.packageName;
                boolean isSelected = selectedApps.contains(packageName);
                boolean isSystemApp = (packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

                // Skip some system packages that are not actual apps
                if (!shouldSkipPackage(packageName)) {
                    appList.add(new AppInfo(appName, packageName, packageInfo.loadIcon(pm), isSelected, isSystemApp));
                }
            }

            // Sort apps by name
            Collections.sort(appList, (o1, o2) -> o1.appName.compareToIgnoreCase(o2.appName));

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                appAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private boolean shouldSkipPackage(String packageName) {
        // Skip system packages that are not actual user apps
        String[] skipPackages = {
            "android",
            "com.android.systemui",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.partnersetup",
            "com.google.android.onetimeinitializer",
            "com.android.providers.downloads",
            "com.android.providers.media",
            "com.android.externalstorage",
            "com.android.providers.calendar",
            "com.android.providers.contacts",
            "com.android.settings",
            "com.android.captiveportallogin",
            "com.android.shell"
        };

        for (String skip : skipPackages) {
            if (packageName.startsWith(skip)) {
                return true;
            }
        }
        return false;
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
