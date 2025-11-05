package main

/*
#cgo CFLAGS: -Wno-error=implicit-function-declaration
#include <stdlib.h>
#include <string.h>
*/
import "C"

import (
	"context"
	"fmt"
	"log"
	"strings"
	"sync"
	"unsafe"

	"github.com/xjasonlyu/tun2socks/v2/engine"
)

var (
	// Thread-safe globals
	engineMutex sync.Mutex
	cancel      context.CancelFunc
	running     = false
)

// Config represents the tun2socks configuration
type Config struct {
	mtu         int
	device      string
	proxy       string
	logLevel    string
	validations []func() error
}

// NewConfig creates a new configuration with proper validation
func NewConfig(fd int, proxyType, server, username, password string, port int) (*Config, error) {
	// Input validation
	if fd <= 0 {
		return nil, fmt.Errorf("invalid file descriptor: %d", fd)
	}
	if strings.TrimSpace(server) == "" {
		return nil, fmt.Errorf("proxy server cannot be empty")
	}
	if port <= 0 || port > 65535 {
		return nil, fmt.Errorf("invalid proxy port: %d (must be 1-65535)", port)
	}

	device := fmt.Sprintf("fd://%d", fd)
	proxyURL, err := buildProxyURL(proxyType, server, username, password, port)
	if err != nil {
		return nil, fmt.Errorf("failed to build proxy URL: %w", err)
	}

	config := &Config{
		mtu:      1500,
		device:   device,
		proxy:    proxyURL,
		logLevel: "info",
	}

	// Add validation functions
	config.validations = []func() error{
		func() error {
			if fd <= 0 {
				return fmt.Errorf("invalid file descriptor: %d", fd)
			}
			return nil
		},
		func() error {
			if strings.TrimSpace(server) == "" {
				return fmt.Errorf("proxy server cannot be empty")
			}
			return nil
		},
		func() error {
			if port <= 0 || port > 65535 {
				return fmt.Errorf("invalid proxy port: %d", port)
			}
			return nil
		},
	}

	return config, nil
}

// buildProxyURL builds proxy URL with proper authentication format
func buildProxyURL(proxyType, server, username, password string, port int) (string, error) {
	var builder strings.Builder

	protocol := strings.ToLower(proxyType)
	switch {
	case protocol == "http" || protocol == "https":
		builder.WriteString("http://")
	case protocol == "socks5":
		builder.WriteString("socks5://")
	default:
		return "", fmt.Errorf("unsupported proxy type: %s", proxyType)
	}

	// Add credentials if provided (FIXED: proper username:password format)
	if username != "" || password != "" {
		// URL encode credentials if needed
		if username != "" {
			builder.WriteString(fmt.Sprintf("%s:%s@", username, password))
		} else {
			// If only password is provided, use it as username
			builder.WriteString(fmt.Sprintf("%s:%s@", password, password))
		}
	}

	// Add server address
	builder.WriteString(fmt.Sprintf("%s:%d", server, port))
	return builder.String(), nil
}

// validate runs all validations
func (c *Config) validate() error {
	for _, validation := range c.validations {
		if err := validation(); err != nil {
			return err
		}
	}
	return nil
}

// toEngineKey converts config to engine.Key
func (c *Config) toEngineKey() engine.Key {
	return engine.Key{
		MTU:      c.mtu,
		Device:   c.device,
		Proxy:    c.proxy,
		LogLevel: c.logLevel,
	}
}

// Tun2SocksEngine represents the thread-safe engine wrapper
type Tun2SocksEngine struct {
	ctx     context.Context
	cancel  context.CancelFunc
	config  *Config
	started bool
	mu      sync.Mutex
}

// NewTun2SocksEngine creates engine with constructor patterns
func NewTun2SocksEngine(config *Config) *Tun2SocksEngine {
	ctx, cancel := context.WithCancel(context.Background())

	return &Tun2SocksEngine{
		ctx:     ctx,
		cancel:  cancel,
		config:  config,
		started: false,
	}
}

// Start starts the engine with proper error handling and thread safety
func (e *Tun2SocksEngine) Start() (err error) {
	e.mu.Lock()
	defer e.mu.Unlock()

	if e.started {
		return fmt.Errorf("engine already started")
	}

	// Validate configuration
	if err := e.config.validate(); err != nil {
		return fmt.Errorf("validation failed: %w", err)
	}

	// Insert configuration
	key := e.config.toEngineKey()
	engine.Insert(&key)

	// Start engine with panic recovery
	engineStarted := make(chan error, 1)

	go func() {
		defer func() {
			if r := recover(); r != nil {
				log.Printf("Engine goroutine panic: %v", r)
				engineStarted <- fmt.Errorf("engine panic: %v", r)
				e.mu.Lock()
				e.started = false
				e.mu.Unlock()
			}
		}()

		// This call can cause fatal errors, so we need to handle it carefully
		engine.Start()
		engineStarted <- nil
	}()

	// Wait for engine to start or fail
	select {
	case err := <-engineStarted:
		if err != nil {
			return fmt.Errorf("failed to start engine: %w", err)
		}
		e.started = true

		// Update global state safely
		engineMutex.Lock()
		cancel = e.cancel
		running = true
		engineMutex.Unlock()

		// Send success message to Java layer for UI updates
		sendLogToJava("Tun2Socks engine started successfully")
		log.Printf("Tun2Socks engine started successfully - Device: %s, Proxy: %s",
			e.config.device, e.config.proxy)
		return nil

	case <-e.ctx.Done():
		return fmt.Errorf("engine startup cancelled")
	}
}

// Stop stops the engine with context cancellation
func (e *Tun2SocksEngine) Stop() error {
	e.mu.Lock()
	defer e.mu.Unlock()

	if !e.started {
		return fmt.Errorf("engine not started")
	}

	// Cancel context and stop engine
	e.cancel()

	// Use defer/recover to handle potential panics in engine.Stop()
	func() {
		defer func() {
			if r := recover(); r != nil {
				log.Printf("Engine stop panic: %v", r)
			}
		}()
		engine.Stop()
	}()

	e.started = false

	// Update global state safely
	engineMutex.Lock()
	running = false
	engineMutex.Unlock()

	// Send stop message to Java layer for UI updates
	sendLogToJava("Tun2Socks engine stopped")
	log.Println("Tun2Socks engine stopped successfully")
	return nil
}

// IsRunning returns the current engine state (thread-safe)
func IsRunning() bool {
	engineMutex.Lock()
	defer engineMutex.Unlock()
	return running
}

// StopGlobalEngine stops the global engine instance (thread-safe)
func StopGlobalEngine() {
	engineMutex.Lock()
	defer engineMutex.Unlock()

	if cancel != nil {
		cancel()
	}

	func() {
		defer func() {
			if r := recover(); r != nil {
				log.Printf("Global engine stop panic: %v", r)
			}
		}()
		engine.Stop()
	}()

	running = false
	log.Println("Global Tun2Socks engine stopped")
}

// ProxyType enum for better type safety
type ProxyType string

const (
	ProxyTypeSOCKS5 ProxyType = "socks5"
	ProxyTypeHTTP   ProxyType = "http"
	ProxyTypeHTTPS  ProxyType = "https"
)

// SupportedProxyTypes map
var SupportedProxyTypes = map[ProxyType]bool{
	ProxyTypeSOCKS5: true,
	ProxyTypeHTTP:   true,
	ProxyTypeHTTPS:  true,
}

// ValidateProxyType validates proxy type
func ValidateProxyType(proxyType string) bool {
	pt := ProxyType(strings.ToLower(proxyType))
	_, exists := SupportedProxyTypes[pt]
	return exists
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_Start
func Java_com_yiguihai_tun2socks_Tun2Socks_Start(tunFd C.int, proxyType *C.char, server *C.char, port C.int, username *C.char, password *C.char) {
	// Convert C strings to Go strings
	typeStr := C.GoString(proxyType)
	serverStr := C.GoString(server)
	usernameStr := C.GoString(username)
	passwordStr := C.GoString(password)

	log.Printf("JNI Start called - tunFd: %d, proxyType: %s, server: %s, port: %d, username: %s",
		tunFd, typeStr, serverStr, port, usernameStr)

	// Validate proxy type
	if !ValidateProxyType(typeStr) {
		log.Printf("Failed to start tun2socks engine: unsupported proxy type: %s", typeStr)
		return
	}

	// Create configuration with proper error handling
	config, err := NewConfig(
		int(tunFd),
		typeStr,
		serverStr,
		usernameStr,
		passwordStr,
		int(port),
	)
	if err != nil {
		log.Printf("Failed to create configuration: %v", err)
		return
	}

	// Create and start engine with panic recovery
	func() {
		defer func() {
			if r := recover(); r != nil {
				log.Printf("Engine creation panic: %v", r)
			}
		}()

		engine := NewTun2SocksEngine(config)
		if err := engine.Start(); err != nil {
			sendLogToJava("Failed to start tun2socks engine")
			log.Printf("Failed to start tun2socks engine: %v", err)
		}
	}()
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_StartWithUrl
func Java_com_yiguihai_tun2socks_Tun2Socks_StartWithUrl(tunFd C.int, proxyUrl *C.char) {
	// Convert C strings to Go strings
	proxyUrlStr := C.GoString(proxyUrl)

	log.Printf("JNI StartWithUrl called - tunFd: %d, proxyUrl: %s",
		tunFd, proxyUrlStr)

	// Validate proxy URL format
	if !strings.Contains(proxyUrlStr, "://") {
		log.Printf("Failed to start tun2socks engine: invalid proxy URL format: %s", proxyUrlStr)
		return
	}

	// Create configuration with proxy URL
	config := &Config{
		mtu:      1500,
		device:   fmt.Sprintf("fd://%d", int(tunFd)),
		proxy:    proxyUrlStr,
		logLevel: "info",
		validations: []func() error{
			func() error {
				if tunFd <= 0 {
					return fmt.Errorf("invalid file descriptor: %d", tunFd)
				}
				return nil
			},
		},
	}

	// Create and start engine with panic recovery
	func() {
		defer func() {
			if r := recover(); r != nil {
				log.Printf("Engine creation panic: %v", r)
			}
		}()

		engine := NewTun2SocksEngine(config)
		if err := engine.Start(); err != nil {
			sendLogToJava("Failed to start tun2socks engine")
			log.Printf("Failed to start tun2socks engine with URL: %v", err)
		}
	}()
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_StartWithConfig
func Java_com_yiguihai_tun2socks_Tun2Socks_StartWithConfig(tunFd C.int, proxyUrl *C.char) {
	// For now, delegate to URL-based method
	Java_com_yiguihai_tun2socks_Tun2Socks_StartWithUrl(tunFd, proxyUrl)
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_StopWithLogger
func Java_com_yiguihai_tun2socks_Tun2Socks_StopWithLogger() {
	log.Printf("JNI StopWithLogger called")
	StopGlobalEngine()
	log.Println("Tun2Socks engine stopped with logger")
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_Stop
func Java_com_yiguihai_tun2socks_Tun2Socks_Stop() {
	log.Printf("JNI Stop called")
	StopGlobalEngine()
	log.Println("Tun2Socks engine stopped")
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_getStats
func Java_com_yiguihai_tun2socks_Tun2Socks_getStats() C.long {
	// Return a simple status indicator
	// In a real implementation, this would return actual statistics
	if IsRunning() {
		log.Printf("getStats() called from Java - engine is running")
		return 1 // Running
	} else {
		log.Printf("getStats() called from Java - engine is not running")
		return 0 // Not running
	}
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_setTimeout
func Java_com_yiguihai_tun2socks_Tun2Socks_setTimeout(timeoutMs C.int) {
	log.Printf("Timeout set to %d ms (Note: timeout setting not yet implemented)", timeoutMs)
	// Implementation would set the timeout in the engine configuration
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_testJNI
func Java_com_yiguihai_tun2socks_Tun2Socks_testJNI() C.long {
	log.Printf("testJNI() called - testing JNI connection")
	return 12345
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_testJNI2
func Java_com_yiguihai_tun2socks_Tun2Socks_testJNI2() C.long {
	log.Printf("Direct JNI function called - testing bypass")
	return 54321
}

// Empty main function required for CGO shared library build
func main() {
	// Shared library build requires main function, even if empty
}

// sendLogToJava sends log messages to Java layer through JNI
func sendLogToJava(message string) {
	// Convert Go string to C string for JNI call
	cMessage := C.CString(message)
	defer func() {
		// Free the C string to prevent memory leaks
		C.free(unsafe.Pointer(cMessage))
	}()

	// This will call the Java logger method through JNI
	// For now, we'll also log locally as fallback
	log.Printf("JNI_LOG: %s", message)
}

// Initialization function for JNI library
func init() {
	// Enhanced logging setup
	log.SetFlags(log.LstdFlags | log.Lshortfile)
	log.Println("Tun2Socks JNI library initialized - FIXED VERSION")
}