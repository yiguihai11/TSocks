
package com.yiguihai.tun2socks;

public class Tun2Socks {

    // This is the interface that the Go side will call to send logs back to Java.
    public interface Logger {
        void log(String message);
    }

    /**
     * Starts the tun2socks core engine.
     *
     * @param tunFd The file descriptor of the TUN interface.
     * @param proxyType The type of the proxy (e.g., "SOCKS5", "Shadowsocks").
     * @param server The proxy server address.
     * @param port The proxy server port.
     * @param password The proxy password (can be null).
     * @param excludedIps A string containing IPs/CIDRs to exclude, separated by newlines.
     * @param logger The callback object for logging.
     */
    public static native void start(int tunFd, String proxyType, String server, int port, String password, String excludedIps, Logger logger);

    /**
     * Stops the tun2socks core engine.
     */
    public static native void stop();

    // Static block to load the native library.
    // The name "tun2socks" must match the name of the generated .so file.
    static {
        try {
            System.loadLibrary("tun2socks");
        } catch (UnsatisfiedLinkError e) {
            // This will happen if the .so file is not found. 
            // It's expected during development before the Go library is compiled.
            System.err.println("Native code library failed to load. \n" + e);
        }
    }
}
