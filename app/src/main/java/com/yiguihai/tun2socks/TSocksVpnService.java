package com.yiguihai.tun2socks;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

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

                // Retrieve proxy configuration from settings
                String proxyType = prefs.getString(SettingsActivity.PREF_PROXY_PROTOCOL, "SOCKS5");
                String server = prefs.getString(SettingsActivity.PREF_PROXY_SERVER, "");
                String portStr = prefs.getString(SettingsActivity.PREF_PROXY_PORT, "1080");
                String username = prefs.getString(SettingsActivity.PREF_PROXY_USERNAME, "");
                String password = prefs.getString(SettingsActivity.PREF_PROXY_PASSWORD, "");
                String excludedRoutes = prefs.getString(SettingsActivity.PREF_EXCLUDED_IPS, "");

                // Log configuration for debugging
                log("Configuration loaded:");
                log("  Protocol: " + proxyType);
                log("  Server: '" + server + "'");
                log("  Port: " + portStr);
                log("  Username: '" + username + "'");
                log("  Password: " + (password.isEmpty() ? "empty" : "set"));
                log("  ExcludedRoutes: '" + excludedRoutes + "'");

                // Validate configuration - skip for Direct and Reject protocols
                if (!proxyType.equals("Direct") && !proxyType.equals("Reject")) {
                    if (server.isEmpty() || server.trim().isEmpty()) {
                        log("ERROR: Proxy server not configured");
                        log("SOLUTION: Please open Settings and configure proxy server");

                        // Send broadcast to MainActivity to show configuration needed dialog
                        Intent broadcastIntent = new Intent("com.yiguihai.tun2socks.CONFIGURATION_NEEDED");
                        broadcastIntent.putExtra("message", "Proxy server not configured. Please configure proxy settings first.");
                        sendBroadcast(broadcastIntent);

                        throw new Exception("Proxy server not configured in settings. Please configure proxy server first.");
                    }
                }

                int port = 0; // Default port
                // Validate port only for protocols that need it
                if (!proxyType.equals("Direct") && !proxyType.equals("Reject")) {
                    try {
                        port = Integer.parseInt(portStr);
                        if (port <= 0 || port > 65535) {
                            log("ERROR: Invalid proxy port: " + port);
                            throw new Exception("Invalid proxy port: " + port);
                        }
                    } catch (NumberFormatException e) {
                        log("ERROR: Invalid port format: " + portStr);
                        throw new Exception("Invalid port format: " + portStr);
                    }
                }

                // Log configuration (without password)
                if (proxyType.equals("Direct")) {
                    log("Starting tun2socks with Direct connection (no proxy)");
                } else if (proxyType.equals("Reject")) {
                    log("Starting tun2socks with Reject mode (blocking all connections)");
                } else {
                    log(String.format("Starting tun2socks with %s://%s:%d (user: %s)",
                        proxyType.toLowerCase(), server, port, username.isEmpty() ? "none" : username));
                }

                // Start the native tun2socks process with proxy configuration
                Tun2Socks.Start(tunFd.getFd(), proxyType, server, port, username, password);
                log("Native tun2socks started successfully");

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

        // Basic VPN configuration
        boolean ipv4Enabled = prefs.getBoolean(SettingsActivity.PREF_IPV4_ENABLED, true);
        boolean ipv6Enabled = prefs.getBoolean(SettingsActivity.PREF_IPV6_ENABLED, false);

        if (ipv4Enabled) {
            builder.addAddress("10.0.8.1", 24);
            builder.addRoute("0.0.0.0", 0); // Default route for IPv4
            String dnsV4 = prefs.getString(SettingsActivity.PREF_DNS_V4, "8.8.8.8");
            if (!dnsV4.isEmpty()) builder.addDnsServer(dnsV4);
        }

        if (ipv6Enabled) {
            builder.addAddress("fd00::8:1", 120);
            builder.addRoute("::", 0); // Default route for IPv6
            String dnsV6 = prefs.getString(SettingsActivity.PREF_DNS_V6, "2001:4860:4860::8888");
            if (!dnsV6.isEmpty()) builder.addDnsServer(dnsV6);
        }

        // Handle excluded routes automatically
        String excludedRoutes = prefs.getString(SettingsActivity.PREF_EXCLUDED_IPS, "");
        if (!excludedRoutes.isEmpty()) {
            processExcludedRoutes(builder, excludedRoutes, ipv4Enabled, ipv6Enabled);
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

    /**
     * Process excluded routes and add them to VPN builder
     * Automatically detects IPv4/IPv6 and adds appropriate subnet masks
     */
    private void processExcludedRoutes(Builder builder, String excludedRoutes, boolean ipv4Enabled, boolean ipv6Enabled) {
        String[] routes = excludedRoutes.split(",");

        for (String route : routes) {
            String cleanedRoute = route.trim();
            if (cleanedRoute.isEmpty()) continue;

            try {
                if (isIPv4Address(cleanedRoute)) {
                    if (!ipv4Enabled) continue;

                    if (cleanedRoute.contains("/")) {
                        // Already has CIDR notation
                        builder.addRoute(cleanedRoute.split("/")[0], Integer.parseInt(cleanedRoute.split("/")[1]));
                    } else {
                        // Single IP, add /32 mask
                        builder.addRoute(cleanedRoute, 32);
                    }
                    log("Added excluded IPv4 route: " + cleanedRoute);

                } else if (isIPv6Address(cleanedRoute)) {
                    if (!ipv6Enabled) continue;

                    if (cleanedRoute.contains("/")) {
                        // Already has CIDR notation
                        String[] parts = cleanedRoute.split("/");
                        builder.addRoute(parts[0], Integer.parseInt(parts[1]));
                    } else {
                        // Single IP, add /128 mask
                        builder.addRoute(cleanedRoute, 128);
                    }
                    log("Added excluded IPv6 route: " + cleanedRoute);

                } else {
                    log("Warning: Invalid route format, skipping: " + cleanedRoute);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to process excluded route: " + cleanedRoute, e);
                log("Warning: Invalid route format, skipping: " + cleanedRoute);
            }
        }
    }

    /**
     * Check if string is IPv4 address
     */
    private boolean isIPv4Address(String address) {
        return address.matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$") ||
               address.matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\/\\d{1,2}$");
    }

    /**
     * Check if string is IPv6 address
     */
    private boolean isIPv6Address(String address) {
        // Basic IPv6 pattern - simplified version for common cases
        return address.matches("^[0-9a-fA-F:]+(/\\d{1,3})?$") &&
               address.contains(":") &&
               !address.matches(".*\\..*"); // Not IPv4
    }

    private void stopVpn() {
        log("Stopping VPN...");
        Tun2Socks.Stop(); // Call JNI to stop the native process

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