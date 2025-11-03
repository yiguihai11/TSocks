package com.yiguihai.tun2socks;

public class Tun2Socks {

    /**
     * Logger interface for receiving logs from native code.
     */
    public interface Logger {
        void log(String message);
    }

    /**
     * Starts the tun2socks core engine.
     */
    public static native void Start(int tunFd, String proxyType, String server, int port, String password, String excludedIps, Logger logger);

    /**
     * Stops the tun2socks core engine.
     */
    public static native void Stop(Logger logger);

    static {
        // The name "tun2socks" must match the name of the generated .so file (libtun2socks.so)
        System.loadLibrary("tun2socks");
    }
}