package main

import "C"

import (
	"context"
	"fmt"
	"strconv"

	"github.com/xjasonlyu/tun2socks/v2/core"
	"github.com/xjasonlyu/tun2socks/v2/core/device/fdbased"
	"github.com/xjasonlyu/tun2socks/v2/proxy"
)

var cancel context.CancelFunc

//export Start
func Start(tunFd C.int, proxyType *C.char, server *C.char, port C.int, password *C.char, excludedIps *C.char) {
	// Convert C strings to Go strings
	proxyTypeGo := C.GoString(proxyType)
	serverGo := C.GoString(server)
	passwordGo := C.GoString(password)
	portGo := int(port)

	fmt.Printf("Starting with fd: %d, proxy: %s, server: %s:%d\n", tunFd, proxyTypeGo, serverGo, portGo)

	fdStr := strconv.Itoa(int(tunFd))

	// Use the FD-based device model, which is perfect for Android VpnService
	dev, err := fdbased.Open(fdStr, 1500, 0)
	if err != nil {
		fmt.Printf("Failed to create FD-based device: %v\n", err)
		return
	}

	// For now, we only support SOCKS5 as an example.
	// We will expand this later based on proxyTypeGo
	proxyHandler, err := proxy.NewSocks5(serverGo, uint16(portGo), passwordGo)
	if err != nil {
		fmt.Printf("Failed to create proxy handler: %v\n", err)
		return
	}

	var ctx context.Context
	ctx, cancel = context.WithCancel(context.Background())

	go core.Start(ctx, dev, proxyHandler)

	fmt.Println("tun2socks core started successfully.")
}

//export Stop
func Stop() {
	if cancel != nil {
		cancel()
		cancel = nil
		fmt.Println("tun2socks core stopped.")
	}
}

// This main function is required for CGO to compile a shared library.
func main() {}