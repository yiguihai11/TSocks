package com.yiguihai.tun2socks;

public class Tun2Socks {

    /**
     * Starts the tun2socks core engine.
     */
    public static native void Start(int tunFd, String proxyType, String server, int port, String password, String excludedIps);

    /**
     * Stops the tun2socks core engine.
     */
    public static native void Stop();

    static {
        // The name "tun2socks" must match the name of the generated .so file (libtun2socks.so)
        System.loadLibrary("tun2socks");
    }
}