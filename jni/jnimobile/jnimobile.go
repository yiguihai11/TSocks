
package jnimobile

import (
	"context"
	"io"
	"os"

	"github.com/xjasonlyu/tun2socks/v2/core"
	"github.com/xjasonlyu/tun2socks/v2/core/device/tun"
	"github.com/xjasonlyu/tun2socks/v2/proxy"
)

// Logger is an interface for logging, to be implemented in Java.
// gomobile bind will generate the corresponding Java interface.
type Logger interface {
	Log(message string)
}

var (
	cancel context.CancelFunc
	log    Logger
)

// Start the tun2socks core.
func Start(tunFd int, proxyType string, server string, port int, password string, excludedIps string, logger Logger) {
	log = logger

	// Use a file descriptor from the TUN device that the VpnService creates.
	file := os.NewFile(uintptr(tunFd), "")

	// Create a new TUN device.
	dev, err := tun.NewTUN(file)
	if err != nil {
		log.Log("Failed to create TUN device: " + err.Error())
		return
	}

	// Setup the proxy handler.
	// For now, we only support SOCKS5 as an example.
	proxy, err := proxy.NewSocks5(server, uint16(port), password)
	if err != nil {
		log.Log("Failed to create proxy handler: " + err.Error())
		return
	}

	// Create a context with cancellation.
	var ctx context.Context
	ctx, cancel = context.WithCancel(context.Background())

	// Start the core handler.
	go core.Start(ctx, dev, proxy)

	log.Log("tun2socks core started successfully.")
}

// Stop the tun2socks core.
func Stop() {
	if cancel != nil {
		cancel()
		cancel = nil
		log.Log("tun2socks core stopped.")
	}
}
