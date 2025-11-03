package com.yiguihai.tun2socks;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.LinkedList;
import java.util.Queue;

public class MainActivity extends AppCompatActivity {

    private TextView textViewLog;
    private ScrollView scrollViewLog;
    private Button startVpnButton;
    private Button stopVpnButton;

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
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Update to match Material 3 layout IDs
        TextView statusText = findViewById(R.id.status_text);
        TextView statusIndicator = findViewById(R.id.status_indicator);
        MaterialButton connectButton = findViewById(R.id.connect_button);
        TextView uploadText = findViewById(R.id.upload_text);
        TextView downloadText = findViewById(R.id.download_text);
        TextView connectionsText = findViewById(R.id.connections_text);
        TextView logsText = findViewById(R.id.logs_text);
        Button clearLogsButton = findViewById(R.id.clear_logs_button);
        FloatingActionButton fab = findViewById(R.id.fab);

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
            }
        });

        fab.setOnClickListener(v -> openSettings());

        LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, new IntentFilter(TSocksVpnService.ACTION_LOG_BROADCAST));

        addLog("Welcome to TSocks!");
        updateUiForVpnStopped();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver);
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

    private void addLog(String message) {
        runOnUiThread(() -> {
            TextView logsText = findViewById(R.id.logs_text);
            if (logsText == null) return;

            if (logQueue.size() >= MAX_LOG_LINES) {
                logQueue.poll();
            }
            logQueue.add(message);

            StringBuilder logText = new StringBuilder();
            for (String log : logQueue) {
                logText.append(log).append("\n");
            }
            logsText.setText(logText.toString());
        });
    }

    private void updateUiForVpnStarted() {
        isVpnRunning = true;
        // Update Material 3 UI components
        TextView statusText = findViewById(R.id.status_text);
        MaterialButton connectButton = findViewById(R.id.connect_button);
        TextView statusIndicator = findViewById(R.id.status_indicator);

        if (statusText != null) {
            statusText.setText("Connected");
            statusText.setTextColor(getResources().getColor(R.color.status_connected, null));
        }
        if (connectButton != null) {
            connectButton.setText("Disconnect");
        }
        if (statusIndicator != null) {
            statusIndicator.setBackgroundColor(getResources().getColor(R.color.status_connected, null));
        }
    }

    private void updateUiForVpnStopped() {
        isVpnRunning = false;
        // Update Material 3 UI components
        TextView statusText = findViewById(R.id.status_text);
        MaterialButton connectButton = findViewById(R.id.connect_button);
        TextView statusIndicator = findViewById(R.id.status_indicator);

        if (statusText != null) {
            statusText.setText("Disconnected");
            statusText.setTextColor(getResources().getColor(R.color.status_disconnected, null));
        }
        if (connectButton != null) {
            connectButton.setText("Connect");
        }
        if (statusIndicator != null) {
            statusIndicator.setBackgroundColor(getResources().getColor(R.color.status_disconnected, null));
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}