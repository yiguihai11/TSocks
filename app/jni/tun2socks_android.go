package main

import "C"

import (
	"context"
	"fmt"
	"strconv"

	"github.com/xjasonlyu/tun2socks/v2/engine"
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

	// Create server address with port for proxy
	serverAddr := fmt.Sprintf("%s:%d", serverGo, portGo)

	// Create proxy string in format: protocol://[user:pass@]server:port
	proxyStr := fmt.Sprintf("socks5://%s:%s@%s", passwordGo, passwordGo, serverAddr)

	// For now, we only support SOCKS5, but can be expanded based on proxyTypeGo
	switch proxyTypeGo {
	case "socks5":
		proxyStr = fmt.Sprintf("socks5://%s:%s@%s", passwordGo, passwordGo, serverAddr)
	case "http":
		proxyStr = fmt.Sprintf("http://%s:%s@%s", passwordGo, passwordGo, serverAddr)
	default:
		proxyStr = fmt.Sprintf("socks5://%s:%s@%s", passwordGo, passwordGo, serverAddr)
	}

	// Create device string in file descriptor format: fd://<fd>
	deviceStr := fmt.Sprintf("fd://%s", fdStr)

	// Create engine key with file descriptor device and proxy configuration
	key := &engine.Key{
		MTU:     1500,
		Device:  deviceStr,  // Use file descriptor device format
		Proxy:   proxyStr,   // Use proxy string format
		LogLevel: "info",
	}

	// Insert the key to the engine
	engine.Insert(key)

	// Start the engine in a goroutine
	go engine.Start()

	fmt.Printf("tun2socks core started successfully with device: %s, proxy: %s\n", deviceStr, proxyStr)
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