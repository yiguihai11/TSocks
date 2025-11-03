package main

import "C"

import (
	"context"
	"fmt"
	"strconv"
	"strings"

	"github.com/xjasonlyu/tun2socks/v2/core/device/fdbased"
	"github.com/xjasonlyu/tun2socks/v2/engine"
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

	// Create server address with port
	serverAddr := fmt.Sprintf("%s:%d", serverGo, portGo)

	// Use the FD-based device model, which is perfect for Android VpnService
	dev, err := fdbased.Open(fdStr, 1500, 0)
	if err != nil {
		fmt.Printf("Failed to create FD-based device: %v\n", err)
		return
	}

	// For now, we only support SOCKS5 as an example.
	// We will expand this later based on proxyTypeGo
	proxyHandler, err := proxy.NewSocks5(serverAddr, passwordGo, "")
	if err != nil {
		fmt.Printf("Failed to create proxy handler: %v\n", err)
		return
	}

	var ctx context.Context
	ctx, cancel = context.WithCancel(context.Background())

	// Create engine key with the required configurations
	key := &engine.Key{
		Device: dev,
		Proxy:  proxyHandler,
		// Add other necessary configurations
	}

	// Insert the key to the engine
	engine.Insert(key)

	// Start the engine in a goroutine
	go engine.Start()

	fmt.Println("tun2socks core started successfully.")
}

//export Stop
func Stop() {
	if cancel != nil {
		cancel()
		cancel = nil
	}

	// Stop the engine
	engine.Stop()
	fmt.Println("tun2socks core stopped.")
}

// This main function is required for CGO to compile a shared library.
func main() {}