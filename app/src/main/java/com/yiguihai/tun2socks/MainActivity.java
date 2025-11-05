package com.yiguihai.tun2socks;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.VpnService;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;

import java.util.LinkedList;
import java.util.Queue;

public class MainActivity extends AppCompatActivity {

    // UI state
    private boolean isVpnRunning = false;

    // Legacy UI references (kept for compatibility)
    private TextView textViewLog;
    private ScrollView scrollViewLog;
    private Button startVpnButton;
    private Button stopVpnButton;

    // Configuration display
    private TextView configText;

    private final Queue<String> logQueue = new LinkedList<>();
    private static final int MAX_LOG_LINES = 100; // Increased for better context

    private final ActivityResultLauncher<Intent> vpnPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    startVpnService();
                } else {
                    showToast("VPN permission was denied.");
                }
            });

    private final BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra(TSocksVpnService.EXTRA_LOG_MESSAGE);
            if (message != null) {
                addLog(message);

                // Handle special messages for UI updates
                if (message.contains("Tun2Socks engine started successfully")) {
                    runOnUiThread(() -> {
                        isVpnRunning = true;
                        updateUiForVpnStarted();
                    });
                } else if (message.contains("Tun2Socks engine stopped") || message.contains("Failed to start tun2socks engine")) {
                    runOnUiThread(() -> {
                        isVpnRunning = false;
                        updateUiForVpnStopped();
                    });
                }
            }
        }
    };

    private final BroadcastReceiver configNeededReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            if (message != null) {
                runOnUiThread(() -> {
                    isVpnRunning = false;
                    updateUiForVpnStopped();

                    // Show dialog to configure proxy settings
                    androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("Configuration Required")
                           .setMessage(message + "\n\nWould you like to open Settings to configure proxy now?")
                           .setPositiveButton("Open Settings", (dialog, which) -> {
                               openSettings();
                           })
                           .setNegativeButton("Cancel", (dialog, which) -> {
                               dialog.dismiss();
                           })
                           .setCancelable(false)
                           .show();
                });
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Update to match Material 3 layout IDs
        TextView statusText = findViewById(R.id.status_text);
        MaterialCardView statusIndicator = findViewById(R.id.status_indicator);
        MaterialButton connectButton = findViewById(R.id.connect_button);
        TextView uploadText = findViewById(R.id.upload_text);
        TextView downloadText = findViewById(R.id.download_text);
        TextView connectionsText = findViewById(R.id.connections_text);
        TextView logsText = findViewById(R.id.logs_text);
        Button clearLogsButton = findViewById(R.id.clear_logs_button);
        FloatingActionButton fab = findViewById(R.id.fab);
        configText = findViewById(R.id.config_text);

        // Set up click listeners
        connectButton.setOnClickListener(v -> {
            if (isVpnRunning) {
                stopVpnService();
            } else {
                prepareAndStartVpn();
            }
        });

        clearLogsButton.setOnClickListener(v -> {
            if (logsText != null) {
                logsText.setText("Logs cleared...\n");
                addLog("Logs cleared by user");
            }
        });

        // Find the settings and apps buttons
        MaterialButton settingsButton = findViewById(R.id.settings_button);
        MaterialButton appsButton = findViewById(R.id.apps_button);
        MaterialButton statsButton = findViewById(R.id.stats_button);
        MaterialButton refreshButton = findViewById(R.id.refresh_button);

        settingsButton.setOnClickListener(v -> openSettings());

        appsButton.setOnClickListener(v -> openAppSelection());

        statsButton.setOnClickListener(v -> showStatistics());

        refreshButton.setOnClickListener(v -> refreshStats());

        // FAB should act as a quick toggle for VPN connection
        fab.setOnClickListener(v -> {
            if (isVpnRunning) {
                showToast("Disconnecting VPN...");
                stopVpnService();
            } else {
                showToast("Connecting VPN...");
                prepareAndStartVpn();
            }
        });

        // Set initial FAB icon
        fab.setImageResource(android.R.drawable.ic_media_play);

        LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, new IntentFilter(TSocksVpnService.ACTION_LOG_BROADCAST));
        LocalBroadcastManager.getInstance(this).registerReceiver(configNeededReceiver, new IntentFilter("com.yiguihai.tun2socks.CONFIGURATION_NEEDED"));

        addLog("Welcome to TSocks!");
        updateConfigurationDisplay();
        updateUiForVpnStopped();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(configNeededReceiver);
    }

    private void prepareAndStartVpn() {
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent);
        } else {
            startVpnService();
        }
    }

    private void startVpnService() {
        Intent intent = new Intent(this, TSocksVpnService.class);
        startService(intent);
        updateUiForVpnStarted();
    }

    private void stopVpnService() {
        Intent intent = new Intent(this, TSocksVpnService.class);
        stopService(intent);
        updateUiForVpnStopped();
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void openAppSelection() {
        Intent intent = new Intent(this, AppSelectionActivity.class);
        startActivity(intent);
    }

    private void addLog(String message) {
        runOnUiThread(() -> {
            TextView logsText = findViewById(R.id.logs_text);
            if (logsText == null) return;

            if (logQueue.size() >= MAX_LOG_LINES) {
                logQueue.poll();
            }

            // Add line number for debugging log truncation
            String numberedMessage = "[" + (logQueue.size() + 1) + "] " + message;
            logQueue.add(numberedMessage);

            StringBuilder logText = new StringBuilder();
            for (String log : logQueue) {
                logText.append(log).append("\n");
            }
            logsText.setText(logText.toString());

            // Auto-scroll to bottom, but only if the user is already at the bottom.
            androidx.core.widget.NestedScrollView scrollView = findViewById(R.id.scrollView_logs);
            if (scrollView != null) {
                View view = (View) scrollView.getChildAt(0);
                // Calculate the scrolldelta, if 0, we are at the bottom
                int scrollDelta = view.getBottom() - (scrollView.getHeight() + scrollView.getScrollY());
                if (scrollDelta == 0) {
                    scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                }
            }
        });
    }

    private void updateUiForVpnStarted() {
        isVpnRunning = true;
        // Update Material 3 UI components
        TextView statusText = findViewById(R.id.status_text);
        MaterialButton connectButton = findViewById(R.id.connect_button);
        MaterialCardView statusIndicator = findViewById(R.id.status_indicator);
        FloatingActionButton fab = findViewById(R.id.fab);

        if (statusText != null) {
            statusText.setText("Connected");
            statusText.setTextColor(getResources().getColor(R.color.status_connected, null));
        }
        if (connectButton != null) {
            connectButton.setText("Disconnect");
        }
        if (statusIndicator != null) {
            statusIndicator.setCardBackgroundColor(getResources().getColor(R.color.status_connected, null));
        }
        if (fab != null) {
            // Set FAB icon to disconnect icon
            fab.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        }
    }

    private void updateUiForVpnStopped() {
        isVpnRunning = false;
        // Update Material 3 UI components
        TextView statusText = findViewById(R.id.status_text);
        MaterialButton connectButton = findViewById(R.id.connect_button);
        MaterialCardView statusIndicator = findViewById(R.id.status_indicator);
        FloatingActionButton fab = findViewById(R.id.fab);

        if (statusText != null) {
            statusText.setText("Disconnected");
            statusText.setTextColor(getResources().getColor(R.color.status_disconnected, null));
        }
        if (connectButton != null) {
            connectButton.setText("Connect");
        }
        if (statusIndicator != null) {
            statusIndicator.setCardBackgroundColor(getResources().getColor(R.color.status_disconnected, null));
        }
        if (fab != null) {
            // Set FAB icon to connect icon
            fab.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    
  
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        } else if (id == R.id.action_help) {
            showHelpDialog();
            return true;
        } else if (id == R.id.action_export_config) {
            exportConfiguration();
            return true;
        } else if (id == R.id.action_import_config) {
            importConfiguration();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("About TSocks")
               .setMessage("TSocks - Modern VPN Client\n\nVersion: 1.0\n\nFeatures:\n• SOCKS5/HTTP Proxy Support\n• Shadowsocks Protocol\n• Material 3 Design\n• Real-time Statistics\n\nBuilt with ❤️ using Go and Android")
               .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
               .show();
    }

    private void showHelpDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Help & Guide")
               .setMessage("How to use TSocks:\n\n" +
                       "1. Configure Proxy:\n" +
                       "   • Tap Settings button\n" +
                       "   • Enter proxy server details\n" +
                       "   • Select protocol type\n\n" +
                       "2. Select Apps (Optional):\n" +
                       "   • Tap Apps button\n" +
                       "   • Choose which apps use VPN\n\n" +
                       "3. Connect:\n" +
                       "   • Tap Connect or FAB button\n" +
                       "   • Grant VPN permission\n" +
                       "   • Monitor connection status\n\n" +
                       "Need help? Contact support!")
               .setPositiveButton("Got it", (dialog, which) -> dialog.dismiss())
               .show();
    }

    private void exportConfiguration() {
        // TODO: Implement configuration export functionality
        showToast("Export feature coming soon!");
    }

    private void importConfiguration() {
        // TODO: Implement configuration import functionality
        showToast("Import feature coming soon!");
    }

    private void showStatistics() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Connection Statistics")
               .setMessage("Connection Status: " + (isVpnRunning ? "Connected" : "Disconnected") +
                          "\n\nData Statistics:" +
                          "\n• Upload: Check main interface" +
                          "\n• Download: Check main interface" +
                          "\n• Active Connections: Check main interface" +
                          "\n\nSession Info:" +
                          "\n• Session Duration: " + (isVpnRunning ? "Active" : "Not started") +
                          "\n• Protocol: Configured in Settings" +
                          "\n• Server: Configured in Settings")
               .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
               .show();
    }

    private void refreshStats() {
        // Update statistics display
        if (isVpnRunning) {
            addLog("Statistics refreshed");
            showToast("Statistics refreshed");
        } else {
            showToast("Connect to VPN to view statistics");
        }
    }

    private void updateConfigurationDisplay() {
        if (configText == null) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        String protocol = prefs.getString(SettingsActivity.PREF_PROXY_PROTOCOL, "SOCKS5");
        String server = prefs.getString(SettingsActivity.PREF_PROXY_SERVER, "");
        String port = prefs.getString(SettingsActivity.PREF_PROXY_PORT, "");
        String username = prefs.getString(SettingsActivity.PREF_PROXY_USERNAME, "");
        String ipv4Enabled = prefs.getBoolean(SettingsActivity.PREF_IPV4_ENABLED, true) ? "Enabled" : "Disabled";
        String ipv6Enabled = prefs.getBoolean(SettingsActivity.PREF_IPV6_ENABLED, false) ? "Enabled" : "Disabled";
        String dnsV4 = prefs.getString(SettingsActivity.PREF_DNS_V4, "8.8.8.8");
        String dnsV6 = prefs.getString(SettingsActivity.PREF_DNS_V6, "2001:4860:4860::8888");

        StringBuilder config = new StringBuilder();
        config.append("Protocol: ").append(protocol).append("\n");

        if (!server.isEmpty() && !port.isEmpty()) {
            config.append("Server: ").append(server).append(":").append(port).append("\n");
            if (!username.isEmpty()) {
                config.append("Username: ").append(username).append("\n");
            }
        } else {
            config.append("Server: Not configured\n");
        }

        config.append("IPv4: ").append(ipv4Enabled).append(" | ");
        config.append("IPv6: ").append(ipv6Enabled).append("\n");
        config.append("DNS: ").append(dnsV4);
        if (ipv6Enabled.equals("Enabled")) {
            config.append(", ").append(dnsV6);
        }

        configText.setText(config.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update configuration display when returning from settings
        updateConfigurationDisplay();
    }
}