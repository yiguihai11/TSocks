package com.yiguihai.tun2socks;

public class Tun2Socks {

    /**
     * Supported proxy protocols based on tun2socks v2.
     */
    public enum ProxyProtocol {
        SOCKS5("socks5", "SOCKS5"),
        SOCKS4("socks4", "SOCKS4"),
        HTTP("http", "HTTP"),
        SHADOWSOCKS("ss", "Shadowsocks"),
        RELAY("relay", "Relay"),
        DIRECT("direct", "Direct"),
        REJECT("reject", "Reject");

        private final String protocol;
        private final String displayName;

        ProxyProtocol(String protocol, String displayName) {
            this.protocol = protocol;
            this.displayName = displayName;
        }

        public String getProtocol() { return protocol; }
        public String getDisplayName() { return displayName; }

        public static ProxyProtocol fromString(String protocol) {
            for (ProxyProtocol p : values()) {
                if (p.protocol.equalsIgnoreCase(protocol)) {
                    return p;
                }
            }
            return SOCKS5; // Default fallback
        }
    }

    /**
     * Logger interface for receiving logs from native code.
     */
    public interface Logger {
        void log(String message);
    }

    /**
     * Proxy configuration class.
     */
    public static class ProxyConfig {
        public ProxyProtocol protocol;
        public String server;
        public int port;
        public String username;
        public String password;
        public String excludedIps;

        public ProxyConfig(ProxyProtocol protocol, String server, int port,
                          String username, String password, String excludedIps) {
            this.protocol = protocol;
            this.server = server;
            this.port = port;
            this.username = username;
            this.password = password;
            this.excludedIps = excludedIps;
        }

        public String toUrl() {
            switch (protocol) {
                case SOCKS5:
                    return String.format("socks5://%s:%s@%s:%d", username, password, server, port);
                case SOCKS4:
                    return String.format("socks4://%s:%s@%s:%d", username, password, server, port);
                case HTTP:
                    return String.format("http://%s:%s@%s:%d", username, password, server, port);
                case SHADOWSOCKS:
                    // Shadowsocks format: ss://method:password@server:port
                    return String.format("ss://%s:%s@%s:%d", "aes-256-gcm", password, server, port);
                case RELAY:
                    return String.format("relay://%s:%d", server, port);
                default:
                    return String.format("socks5://%s:%s@%s:%d", username, password, server, port);
            }
        }
    }

    /**
     * Connection status callback interface.
     */
    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }

    /**
     * Starts the tun2socks core engine (legacy method for compatibility).
     */
    public static native void Start(int tunFd, String proxyType, String server, int port, String password, String excludedIps);

    /**
     * Starts the tun2socks core engine with enhanced configuration.
     */
    public static native void StartWithUrl(int tunFd, String proxyUrl, String excludedIps);

    /**
     * Starts the tun2socks core engine with proxy config.
     */
    public static native void StartWithConfig(int tunFd, String proxyUrl, String excludedIps);

    /**
     * Stops the tun2socks core engine (legacy method).
     */
    public static native void Stop();

    /**
     * Stops the tun2socks core engine with logger.
     */
    public static native void StopWithLogger();

    /**
     * Gets connection statistics.
     */
    public static native long getStats();

    /**
     * Sets connection timeout.
     */
    public static native void setTimeout(int timeoutMs);

    static {
        // The name "tun2socks" must match the name of the generated .so file (libtun2socks.so)
        System.loadLibrary("tun2socks");
    }
}