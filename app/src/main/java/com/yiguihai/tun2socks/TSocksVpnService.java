package com.yiguihai.tun2socks;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.HttpURLConnection;
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

                // Test native library availability before starting
                try {
                    log("Testing native library connectivity...");
                    int testResult = Tun2Socks.testJNI();
                    log("Native library test result: " + testResult);
                } catch (UnsatisfiedLinkError e) {
                    log("ERROR: Native library not available or incompatible: " + e.getMessage());
                    throw new Exception("Native library error: " + e.getMessage());
                } catch (Exception e) {
                    log("WARNING: Native library test failed, continuing: " + e.getMessage());
                }

                // Add a small delay to ensure VPN interface is fully ready
                Thread.sleep(100);

                // Start the native tun2socks process based on protocol with enhanced error handling
                try {
                    log("DEBUG: Starting native tun2socks with TUN FD: " + tunFd.getFd());
                    log("DEBUG: Tun2Socks library loaded and available");

                    if (proxyType.equalsIgnoreCase("Direct")) {
                        log("Starting tun2socks with Direct connection (no proxy)");
                        log("DEBUG: Using URL: direct://");
                        log("DEBUG: Alternative direct URL would be: direct://0.0.0.0/0");

                        // Try direct:// format first
                        try {
                            Tun2Socks.StartWithUrl(tunFd.getFd(), "direct://");
                            log("DEBUG: direct:// URL accepted by native library");
                        } catch (Exception e) {
                            log("WARNING: direct:// URL failed: " + e.getMessage());
                            log("This might indicate the native library doesn't support this URL format");
                            throw e;
                        }
                    } else if (proxyType.equalsIgnoreCase("Reject")) {
                        log("Starting tun2socks with Reject mode (blocking all connections)");
                        log("DEBUG: Using URL: reject://");
                        Tun2Socks.StartWithUrl(tunFd.getFd(), "reject://");
                    } else {
                        // Enhanced proxy parameter validation
                        log("=== PROXY CONFIGURATION VALIDATION ===");
                        log("Protocol: " + proxyType);
                        log("Server: '" + server + "'");
                        log("Port: '" + portStr + "'");
                        log("Username: '" + username + "'");
                        log("Password length: " + (password != null ? password.length() : 0));

                        int port = 0;
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

                        // Validate server address
                        if (server == null || server.trim().isEmpty()) {
                            log("ERROR: Proxy server is empty or null");
                            throw new Exception("Proxy server is required");
                        }

                        // Sanitize parameters for native library
                        String cleanServer = server.trim();
                        String cleanUsername = username != null ? username.trim() : "";
                        String cleanPassword = password != null ? password.trim() : "";

                        log("=== SANITIZED PARAMETERS ===");
                        log("Clean server: '" + cleanServer + "'");
                        log("Clean port: " + port);
                        log("Clean username: '" + cleanUsername + "'");
                        log("Has password: " + !cleanPassword.isEmpty());

                        log(String.format("Starting tun2socks with %s://%s:%d (user: %s)",
                            proxyType.toLowerCase(), cleanServer, port, cleanUsername.isEmpty() ? "none" : cleanUsername));
                        log("DEBUG: Using traditional Start method with parameters");

                        // Add additional safety check before native call
                        try {
                            log("DEBUG: About to call native Tun2Socks.Start() method");
                            log("DEBUG: Parameters - FD: " + tunFd.getFd() + ", Type: " + proxyType +
                                ", Server: " + cleanServer + ", Port: " + port +
                                ", User: " + cleanUsername + ", PassLen: " + cleanPassword.length());

                            Tun2Socks.Start(tunFd.getFd(), proxyType, cleanServer, port, cleanUsername, cleanPassword);
                            log("DEBUG: Native Tun2Socks.Start() completed successfully");

                        } catch (UnsatisfiedLinkError e) {
                            log("FATAL: Native library error during SOCKS5 start: " + e.getMessage());
                            log("This may indicate parameter format incompatibility");
                            throw new Exception("Native library SOCKS5 error: " + e.getMessage());
                        } catch (Exception e) {
                            log("ERROR: SOCKS5 native start failed: " + e.getMessage());
                            throw new Exception("SOCKS5 start failed: " + e.getMessage());
                        }
                    }

                    // Add delay to verify native startup and test connectivity
                    Thread.sleep(500);

                    // Test network connectivity after startup
                    testNetworkConnectivity();

                    // Get connection statistics from native library
                    try {
                        int stats = Tun2Socks.getStats();
                        log("Native library stats: " + stats);

                        // Test if native library is actually processing data
                        if (stats == 0) {
                            log("WARNING: No traffic processed yet, this might indicate a routing issue");

                            // Add more detailed diagnostics
                            new Thread(() -> {
                                try {
                                    Thread.sleep(1000); // Wait 1 second
                                    int stats2 = Tun2Socks.getStats();
                                    log("Stats after 1 second: " + stats2);

                                    if (stats2 == 0) {
                                        log("DIAGNOSIS: No traffic is flowing through the VPN tunnel");
                                        log("This suggests a routing or configuration problem");

                                        // Test network interface status
                                        try {
                                            java.net.NetworkInterface tunInterface = java.net.NetworkInterface.getByName("tun0");
                                            if (tunInterface != null) {
                                                log("TUN interface found: " + tunInterface.toString());
                                                log("TUN interface is up: " + tunInterface.isUp());
                                            } else {
                                                log("WARNING: TUN interface not found in NetworkInterface list");
                                            }
                                        } catch (Exception e) {
                                            log("Could not check TUN interface: " + e.getMessage());
                                        }
                                    }
                                } catch (Exception e) {
                                    log("Error during stats monitoring: " + e.getMessage());
                                }
                            }).start();
                        }
                    } catch (Exception e) {
                        log("WARNING: Could not get stats from native library: " + e.getMessage());
                    }

                    log("Native tun2socks started successfully");

                } catch (UnsatisfiedLinkError e) {
                    log("FATAL: Native library linking error: " + e.getMessage());
                    throw new Exception("Native library linking failed: " + e.getMessage());
                } catch (Exception e) {
                    log("ERROR: Failed to start native tun2socks: " + e.getMessage());
                    throw new Exception("Native startup failed: " + e.getMessage());
                }

            } catch (Exception e) {
                Log.e(TAG, "VPN thread error", e);
                log("Error: " + e.getMessage());

                // Send error broadcast to UI
                Intent errorIntent = new Intent("com.yiguihai.tun2socks.VPN_ERROR");
                errorIntent.putExtra("error", e.getMessage());
                sendBroadcast(errorIntent);

                stopSelf(); // Stop service if startup fails
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

        // Get DNS settings for logging
        String dnsV4 = prefs.getString(SettingsActivity.PREF_DNS_V4, "8.8.8.8");
        String dnsV6 = prefs.getString(SettingsActivity.PREF_DNS_V6, "2001:4860:4860::8888");

        if (ipv4Enabled) {
            builder.addAddress("10.0.8.1", 24);
            builder.addRoute("0.0.0.0", 0); // Default route for IPv4
            if (!dnsV4.isEmpty()) {
                builder.addDnsServer(dnsV4);
                log("DEBUG: Added IPv4 DNS server: " + dnsV4);
            }
        }

        if (ipv6Enabled) {
            builder.addAddress("fd00::8:1", 120);
            builder.addRoute("::", 0); // Default route for IPv6
            if (!dnsV6.isEmpty()) {
                builder.addDnsServer(dnsV6);
                log("DEBUG: Added IPv6 DNS server: " + dnsV6);
            }
        }

        // Add additional DNS servers to ensure connectivity
        log("DEBUG: Adding backup DNS servers for reliability");
        builder.addDnsServer("1.1.1.1");     // Cloudflare
        builder.addDnsServer("208.67.222.222"); // OpenDNS

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

        // IMPORTANT: Exclude our own app from VPN so we can test network connectivity
        // This prevents circular routing where VPN traffic tries to go through itself
        try {
            String packageName = getPackageName();
            log("DEBUG: Excluding own package from VPN: " + packageName);
            builder.addDisallowedApplication(packageName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to exclude own package", e);
        }

        ParcelFileDescriptor pfd = builder.establish();
        if (pfd == null) {
            throw new IOException("Failed to establish VPN interface.");
        }

        // Log network configuration for debugging
        log("=== VPN Configuration Summary ===");
        log("VPN Address: " + (ipv4Enabled ? "10.0.8.1/24" : "") + (ipv4Enabled && ipv6Enabled ? ", " : "") + (ipv6Enabled ? "fd00::8:1/120" : ""));
        log("Default Routes: IPv4=" + ipv4Enabled + ", IPv6=" + ipv6Enabled);
        log("DNS Servers: " + (ipv4Enabled && !dnsV4.isEmpty() ? dnsV4 : "") +
            (ipv4Enabled && !dnsV4.isEmpty() ? ", " : "") +
            (ipv6Enabled && !dnsV6.isEmpty() ? dnsV6 : "") +
            ", 1.1.1.1, 208.67.222.222");
        log("MTU: " + mtu);
        log("TUN FD: " + pfd.getFd());
        log("=== VPN Configuration Complete ===");

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
        try {
            // Try to stop native process with enhanced error handling
            Tun2Socks.Stop(); // Call JNI to stop the native process
            log("Native tun2socks stopped");
        } catch (UnsatisfiedLinkError e) {
            log("WARNING: Native library error during stop: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error stopping native tun2socks", e);
        }

        if (tunFd != null) {
            try {
                tunFd.close();
                log("TUN file descriptor closed");
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

    /**
     * Test network connectivity after VPN startup
     */
    private void testNetworkConnectivity() {
        log("=== Testing Network Connectivity ===");

        // Test DNS resolution with IPv4 preference
        try {
            log("Testing DNS resolution for google.com...");
            // Force IPv4 preference to avoid IPv6 routing issues
            System.setProperty("java.net.preferIPv4Stack", "true");
            System.setProperty("java.net.preferIPv6Addresses", "false");

            // Test IPv4-only resolution first
            log("Testing IPv4-only DNS resolution...");
            InetAddress[] ipv4Addresses = InetAddress.getAllByName("google.com");

            log("DNS resolution successful: " + ipv4Addresses.length + " addresses found");

            // Prioritize IPv4 addresses and prefer them for connections
            InetAddress ipv4Address = null;
            for (InetAddress addr : ipv4Addresses) {
                String ip = addr.getHostAddress();
                if (ip.contains(".")) { // IPv4
                    log("  - " + ip + " (IPv4) ⭐ PREFERRED");
                    if (ipv4Address == null) {
                        ipv4Address = addr; // Use first IPv4 address
                    }
                } else { // IPv6
                    log("  - " + ip + " (IPv6) - will be ignored");
                }
            }

            if (ipv4Address != null) {
                log("SUCCESS: Will use IPv4 address: " + ipv4Address.getHostAddress());
            } else {
                log("WARNING: No IPv4 addresses found, this may cause connectivity issues");
            }

            // Test simple TCP connection to google.com on port 443
            new Thread(() -> {
                try {
                    log("Testing TCP connection to google.com:443...");
                    java.net.Socket socket = new java.net.Socket();
                    socket.connect(new java.net.InetSocketAddress("google.com", 443), 5000);
                    socket.close();
                    log("TCP connection to google.com:443: SUCCESS");
                } catch (Exception e) {
                    log("ERROR: TCP connection to google.com:443 failed: " + e.getMessage());
                }
            }).start();

            // Test alternative connectivity to a simple service
            new Thread(() -> {
                try {
                    log("Testing connectivity to http://example.com...");
                    URL url = new URL("http://example.com");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    conn.setRequestMethod("GET");

                    int responseCode = conn.getResponseCode();
                    log("example.com response code: " + responseCode);
                    if (responseCode == 200) {
                        log("example.com connectivity: SUCCESS");
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    log("ERROR: example.com connectivity failed: " + e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            log("ERROR: DNS resolution failed: " + e.getMessage());
        }

        // Test HTTPS connectivity (in a separate thread to avoid blocking)
        new Thread(() -> {
            try {
                log("Testing HTTPS connectivity to https://httpbin.org/ip...");
                URL url = new URL("https://httpbin.org/ip");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                log("HTTPS response code: " + responseCode);

                if (responseCode == 200) {
                    String response = new String(conn.getInputStream().readAllBytes());
                    log("HTTPS response: " + response);
                } else {
                    log("HTTPS request failed with code: " + responseCode);
                }
                conn.disconnect();
            } catch (Exception e) {
                log("ERROR: HTTPS connectivity test failed: " + e.getMessage());
            }
        }).start();

        // Test basic ping functionality
        try {
            log("Testing ping to 8.8.8.8...");
            InetAddress address = InetAddress.getByName("8.8.8.8");
            boolean reachable = address.isReachable(3000);
            log("Ping to 8.8.8.8: " + (reachable ? "SUCCESS" : "FAILED"));

            // Additional network diagnostics
            if (reachable) {
                log("SUCCESS: Basic network connectivity confirmed");
            } else {
                log("WARNING: Basic network connectivity failed");
            }
        } catch (Exception e) {
            log("ERROR: Ping test failed: " + e.getMessage());
        }

        // Check if we can actually use the internet by making a real request
        new Thread(() -> {
            try {
                log("Testing actual internet access...");

                // Test with ipinfo.io (simple JSON service)
                URL url = new URL("https://ipinfo.io/json");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "TSocks-VPN/1.0");

                int responseCode = conn.getResponseCode();
                log("ipinfo.io response code: " + responseCode);

                if (responseCode == 200) {
                    String response = new String(conn.getInputStream().readAllBytes());
                    log("ipinfo.io response: " + response.substring(0, Math.min(100, response.length())) + "...");
                    log("INTERNET ACCESS: SUCCESSFUL");
                } else {
                    log("INTERNET ACCESS: FAILED - Response code: " + responseCode);
                }
                conn.disconnect();
            } catch (Exception e) {
                log("ERROR: Internet access test failed: " + e.getMessage());
            }
        }).start();

        log("=== Network Connectivity Test Complete ===");
    }

    @Override
    public void log(String message) {
        Log.d(TAG, "JNI_LOG: " + message);
        Intent intent = new Intent(ACTION_LOG_BROADCAST);
        intent.putExtra(EXTRA_LOG_MESSAGE, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}