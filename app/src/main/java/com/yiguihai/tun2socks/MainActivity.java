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

        textViewLog = findViewById(R.id.text_view_log);
        scrollViewLog = findViewById(R.id.scroll_view_log);
        startVpnButton = findViewById(R.id.button_start_vpn);
        stopVpnButton = findViewById(R.id.button_stop_vpn);
        Button advancedSettingsButton = findViewById(R.id.button_advanced_settings);

        startVpnButton.setOnClickListener(v -> prepareAndStartVpn());
        stopVpnButton.setOnClickListener(v -> stopVpnService());
        advancedSettingsButton.setOnClickListener(v -> openSettings());

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
            boolean isAtBottom = (scrollViewLog.getChildAt(0).getBottom() <= (scrollViewLog.getHeight() + scrollViewLog.getScrollY()));

            if (logQueue.size() >= MAX_LOG_LINES) {
                logQueue.poll();
            }
            logQueue.add(message);

            StringBuilder logText = new StringBuilder();
            for (String log : logQueue) {
                logText.append(log).append("\n");
            }
            textViewLog.setText(logText.toString());

            if (isAtBottom) {
                scrollViewLog.post(() -> scrollViewLog.fullScroll(View.FOCUS_DOWN));
            }
        });
    }

    private void updateUiForVpnStarted() {
        startVpnButton.setEnabled(false);
        stopVpnButton.setEnabled(true);
        ((TextView)findViewById(R.id.text_view_status)).setText("Status: Connected");
    }

    private void updateUiForVpnStopped() {
        startVpnButton.setEnabled(true);
        stopVpnButton.setEnabled(false);
        ((TextView)findViewById(R.id.text_view_status)).setText("Status: Disconnected");
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}