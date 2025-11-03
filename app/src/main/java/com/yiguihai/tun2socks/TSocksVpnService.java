package com.yiguihai.tun2socks;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;

public class TSocksVpnService extends VpnService implements Tun2Socks.Logger {

    private static final String TAG = "TSocksVpnService";
    public static final String ACTION_LOG_BROADCAST = "com.yiguihai.tun2socks.LOG_BROADCAST";
    public static final String EXTRA_LOG_MESSAGE = "log_message";
    private static final String VPN_SESSION_NAME = "TSocks VPN";

    private ParcelFileDescriptor tunFd;
    private Thread vpnThread;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("VPN service starting...");

        vpnThread = new Thread(() -> {
            try {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                tunFd = configureVpn(prefs);
                log("VPN interface configured. TUN FD: " + tunFd.getFd());

                // Retrieve settings and pass them to the JNI layer
                // Note: Proxy type and app selection are not fully implemented yet
                String proxyType = "SOCKS5"; // Placeholder
                String server = prefs.getString("pref_server", ""); // Assuming you add this to settings
                int port = Integer.parseInt(prefs.getString("pref_port", "1080")); // Assuming you add this
                String password = prefs.getString("pref_password", ""); // Assuming you add this
                String excludedIps = prefs.getString(SettingsActivity.PREF_EXCLUDED_IPS, "");

                // Start the native tun2socks process
                Tun2Socks.start(tunFd.getFd(), proxyType, server, port, password, excludedIps, this);

            } catch (Exception e) {
                Log.e(TAG, "VPN thread error", e);
                log("Error: " + e.getMessage());
            } finally {
                stopVpn();
            }
        });

        vpnThread.start();
        return START_STICKY;
    }

    private ParcelFileDescriptor configureVpn(SharedPreferences prefs) throws Exception {
        Builder builder = new Builder();
        builder.setSession(VPN_SESSION_NAME);
        builder.setBlocking(false);

        String mtu = prefs.getString(SettingsActivity.PREF_MTU, "1500");
        builder.setMtu(Integer.parseInt(mtu));

        if (prefs.getBoolean(SettingsActivity.PREF_IPV4_ENABLED, true)) {
            builder.addAddress("10.0.8.1", 24);
            builder.addRoute("0.0.0.0", 0);
            String dnsV4 = prefs.getString(SettingsActivity.PREF_DNS_V4, "8.8.8.8");
            if (!dnsV4.isEmpty()) builder.addDnsServer(dnsV4);
        }

        if (prefs.getBoolean(SettingsActivity.PREF_IPV6_ENABLED, false)) {
            builder.addAddress("fd00::8:1", 120);
            builder.addRoute("::", 0);
            String dnsV6 = prefs.getString(SettingsActivity.PREF_DNS_V6, "2001:4860:4860::8888");
            if (!dnsV6.isEmpty()) builder.addDnsServer(dnsV6);
        }

        // App filtering
        int appFilterMode = prefs.getInt(SettingsActivity.PREF_APP_FILTER_MODE, R.id.radio_button_exclude_mode);
        Set<String> selectedApps = prefs.getStringSet(AppSelectionActivity.PREF_SELECTED_APPS, new HashSet<>());

        if (!selectedApps.isEmpty()) {
            if (appFilterMode == R.id.radio_button_include_mode) {
                for (String appPackage : selectedApps) {
                    try {
                        builder.addAllowedApplication(appPackage);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to add allowed app: " + appPackage, e);
                    }
                }
            } else { // Exclude mode
                for (String appPackage : selectedApps) {
                    try {
                        builder.addDisallowedApplication(appPackage);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to add disallowed app: " + appPackage, e);
                    }
                }
            }
        }

        try {
            builder.addDisallowedApplication(getPackageName());
        } catch (Exception e) {
            Log.e(TAG, "Failed to exclude own package", e);
        }

        ParcelFileDescriptor pfd = builder.establish();
        if (pfd == null) {
            throw new IOException("Failed to establish VPN interface.");
        }
        return pfd;
    }

    private void stopVpn() {
        log("Stopping VPN...");
        Tun2Socks.stop(); // Call JNI to stop the native process

        if (tunFd != null) {
            try {
                tunFd.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close TUN FD", e);
            }
            tunFd = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        log("VPN service destroyed.");
        if (vpnThread != null) {
            vpnThread.interrupt();
        }
        stopVpn();
    }

    @Override
    public void log(String message) {
        Log.d(TAG, "JNI_LOG: " + message);
        Intent intent = new Intent(ACTION_LOG_BROADCAST);
        intent.putExtra(EXTRA_LOG_MESSAGE, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}