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
	"time"
	"unsafe"

	"github.com/xjasonlyu/tun2socks/v2/engine"
)

var (
	// Thread-safe globals
	engineMutex   sync.RWMutex
	engineCtx     context.Context
	engineCancel  context.CancelFunc
	engineRunning = false
	currentEngine *Tun2SocksEngine
)

// Config represents the tun2socks configuration
type Config struct {
	mtu      int
	device   string
	proxy    string
	logLevel string
}

// NewConfig creates a new configuration with proper validation
func NewConfig(fd int, proxyType, server, username, password string, port int) (*Config, error) {
	// Input validation for file descriptor
	if fd <= 0 {
		return nil, fmt.Errorf("invalid file descriptor: %d", fd)
	}

	protocol := strings.ToLower(proxyType)

	// Skip server and port validation for Direct and Reject protocols
	if protocol != "direct" && protocol != "reject" {
		if strings.TrimSpace(server) == "" {
			return nil, fmt.Errorf("proxy server cannot be empty for %s protocol", proxyType)
		}
		if port <= 0 || port > 65535 {
			return nil, fmt.Errorf("invalid proxy port: %d (must be 1-65535)", port)
		}
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
		logLevel: "warning", // Changed to warning to reduce log spam
	}

	return config, nil
}

// buildProxyURL builds proxy URL with proper authentication format
func buildProxyURL(proxyType, server, username, password string, port int) (string, error) {
	protocol := strings.ToLower(proxyType)

	// Handle special protocols that don't need proxy URLs
	switch protocol {
	case "direct":
		return "direct://", nil
	case "reject":
		return "reject://", nil
	}

	var builder strings.Builder

	switch protocol {
	case "http", "https":
		builder.WriteString("http://")
	case "socks5":
		builder.WriteString("socks5://")
	case "socks4":
		builder.WriteString("socks4://")
	case "shadowsocks", "ss":
		builder.WriteString("ss://")
	case "relay":
		builder.WriteString("relay://")
	default:
		return "", fmt.Errorf("unsupported proxy type: %s", proxyType)
	}

	// Add credentials if provided
	username = strings.TrimSpace(username)
	password = strings.TrimSpace(password)
	
	if username != "" && password != "" {
		builder.WriteString(fmt.Sprintf("%s:%s@", username, password))
	} else if password != "" {
		// If only password provided, use it as both username and password
		builder.WriteString(fmt.Sprintf("%s:%s@", password, password))
	}

	// Add server address
	builder.WriteString(fmt.Sprintf("%s:%d", server, port))
	return builder.String(), nil
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
func (e *Tun2SocksEngine) Start() error {
	e.mu.Lock()
	defer e.mu.Unlock()

	if e.started {
		return fmt.Errorf("engine already started")
	}

	// Validate configuration
	if e.config == nil {
		return fmt.Errorf("configuration is nil")
	}

	log.Printf("Starting engine with config: Device=%s, Proxy=%s, MTU=%d",
		e.config.device, e.config.proxy, e.config.mtu)

	// Insert configuration
	key := engine.Key{
		MTU:      e.config.mtu,
		Device:   e.config.device,
		Proxy:    e.config.proxy,
		LogLevel: e.config.logLevel,
	}

	// Safely insert the key
	if err := safeEngineInsert(&key); err != nil {
		return fmt.Errorf("failed to insert engine key: %w", err)
	}

	// Start engine with timeout and panic recovery
	startChan := make(chan error, 1)
	
	go func() {
		defer func() {
			if r := recover(); r != nil {
				log.Printf("Engine panic during start: %v", r)
				startChan <- fmt.Errorf("engine panic: %v", r)
			}
		}()

		// Start the engine
		if err := safeEngineStart(); err != nil {
			startChan <- err
			return
		}
		startChan <- nil
	}()

	// Wait for start with timeout
	select {
	case err := <-startChan:
		if err != nil {
			log.Printf("Engine start failed: %v", err)
			return fmt.Errorf("failed to start engine: %w", err)
		}
		
		e.started = true
		
		// Update global state
		engineMutex.Lock()
		engineCtx = e.ctx
		engineCancel = e.cancel
		engineRunning = true
		currentEngine = e
		engineMutex.Unlock()
		
		log.Printf("Tun2Socks engine started successfully")
		sendLogToJava("Tun2Socks engine started successfully")
		return nil
		
	case <-time.After(10 * time.Second):
		return fmt.Errorf("engine start timeout after 10 seconds")
		
	case <-e.ctx.Done():
		return fmt.Errorf("engine startup cancelled")
	}
}

// Stop stops the engine with context cancellation
func (e *Tun2SocksEngine) Stop() error {
	e.mu.Lock()
	defer e.mu.Unlock()

	if !e.started {
		return nil // Already stopped, not an error
	}

	log.Println("Stopping Tun2Socks engine...")
	
	// Cancel context
	if e.cancel != nil {
		e.cancel()
	}

	// Stop engine with panic recovery
	if err := safeEngineStop(); err != nil {
		log.Printf("Warning during engine stop: %v", err)
	}

	e.started = false

	// Update global state
	engineMutex.Lock()
	engineRunning = false
	currentEngine = nil
	engineMutex.Unlock()

	log.Println("Tun2Socks engine stopped successfully")
	sendLogToJava("Tun2Socks engine stopped")
	return nil
}

// Safe engine operations with panic recovery
func safeEngineInsert(key *engine.Key) (err error) {
	defer func() {
		if r := recover(); r != nil {
			err = fmt.Errorf("panic in engine.Insert: %v", r)
		}
	}()
	engine.Insert(key)
	return nil
}

func safeEngineStart() (err error) {
	defer func() {
		if r := recover(); r != nil {
			err = fmt.Errorf("panic in engine.Start: %v", r)
		}
	}()
	engine.Start()
	return nil
}

func safeEngineStop() (err error) {
	defer func() {
		if r := recover(); r != nil {
			err = fmt.Errorf("panic in engine.Stop: %v", r)
			log.Printf("Recovered from engine.Stop panic: %v", r)
		}
	}()
	engine.Stop()
	return nil
}

// IsRunning returns the current engine state (thread-safe)
func IsRunning() bool {
	engineMutex.RLock()
	defer engineMutex.RUnlock()
	return engineRunning
}

// StopGlobalEngine stops the global engine instance (thread-safe)
func StopGlobalEngine() {
	engineMutex.Lock()
	eng := currentEngine
	engineMutex.Unlock()

	if eng != nil {
		if err := eng.Stop(); err != nil {
			log.Printf("Error stopping global engine: %v", err)
		}
	} else {
		// Still try to stop the engine directly
		safeEngineStop()
		
		engineMutex.Lock()
		engineRunning = false
		engineMutex.Unlock()
		
		log.Println("Global engine stopped (no current engine reference)")
	}
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_Start
func Java_com_yiguihai_tun2socks_Tun2Socks_Start(tunFd C.int, proxyType *C.char, server *C.char, port C.int, username *C.char, password *C.char) {
	// Convert C strings to Go strings safely
	typeStr := safeGoString(proxyType)
	serverStr := safeGoString(server)
	usernameStr := safeGoString(username)
	passwordStr := safeGoString(password)

	log.Printf("JNI Start called - tunFd: %d, proxyType: %s, server: %s, port: %d",
		tunFd, typeStr, serverStr, port)

	// Validate inputs
	if typeStr == "" {
		log.Printf("Error: proxy type is empty")
		sendLogToJava("Failed to start: proxy type is empty")
		return
	}

	// Create configuration with error handling
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
		sendLogToJava(fmt.Sprintf("Failed to start: %v", err))
		return
	}

	// Stop any existing engine
	StopGlobalEngine()
	
	// Wait a bit for cleanup
	time.Sleep(500 * time.Millisecond)

	// Create and start new engine
	engine := NewTun2SocksEngine(config)
	if err := engine.Start(); err != nil {
		log.Printf("Failed to start tun2socks engine: %v", err)
		sendLogToJava(fmt.Sprintf("Failed to start tun2socks engine: %v", err))
		return
	}
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_StartWithUrl
func Java_com_yiguihai_tun2socks_Tun2Socks_StartWithUrl(tunFd C.int, proxyUrl *C.char) {
	proxyUrlStr := safeGoString(proxyUrl)

	log.Printf("JNI StartWithUrl called - tunFd: %d, proxyUrl: %s", tunFd, proxyUrlStr)

	if proxyUrlStr == "" || !strings.Contains(proxyUrlStr, "://") {
		log.Printf("Error: invalid proxy URL format")
		sendLogToJava("Failed to start: invalid proxy URL format")
		return
	}

	config := &Config{
		mtu:      1500,
		device:   fmt.Sprintf("fd://%d", int(tunFd)),
		proxy:    proxyUrlStr,
		logLevel: "warning",
	}

	// Stop any existing engine
	StopGlobalEngine()
	time.Sleep(500 * time.Millisecond)

	engine := NewTun2SocksEngine(config)
	if err := engine.Start(); err != nil {
		log.Printf("Failed to start tun2socks engine with URL: %v", err)
		sendLogToJava(fmt.Sprintf("Failed to start: %v", err))
	}
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_StartWithConfig
func Java_com_yiguihai_tun2socks_Tun2Socks_StartWithConfig(tunFd C.int, proxyUrl *C.char) {
	Java_com_yiguihai_tun2socks_Tun2Socks_StartWithUrl(tunFd, proxyUrl)
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_Stop
func Java_com_yiguihai_tun2socks_Tun2Socks_Stop() {
	log.Printf("JNI Stop called")
	StopGlobalEngine()
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_StopWithLogger
func Java_com_yiguihai_tun2socks_Tun2Socks_StopWithLogger() {
	log.Printf("JNI StopWithLogger called")
	StopGlobalEngine()
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_getStats
func Java_com_yiguihai_tun2socks_Tun2Socks_getStats() C.long {
	if IsRunning() {
		return 1
	}
	return 0
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_setTimeout
func Java_com_yiguihai_tun2socks_Tun2Socks_setTimeout(timeoutMs C.int) {
	log.Printf("Timeout set to %d ms", timeoutMs)
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_testJNI
func Java_com_yiguihai_tun2socks_Tun2Socks_testJNI() C.long {
	log.Printf("testJNI() called")
	return 12345
}

//export Java_com_yiguihai_tun2socks_Tun2Socks_testJNI2
func Java_com_yiguihai_tun2socks_Tun2Socks_testJNI2() C.long {
	log.Printf("testJNI2() called")
	return 54321
}

// Helper function to safely convert C strings
func safeGoString(cStr *C.char) string {
	if cStr == nil {
		return ""
	}
	return strings.TrimSpace(C.GoString(cStr))
}

// sendLogToJava sends log messages to Java layer
func sendLogToJava(message string) {
	log.Printf("JAVA_LOG: %s", message)
}

// Empty main function required for CGO shared library build
func main() {}

// Initialization function
func init() {
	log.SetFlags(log.LstdFlags | log.Lshortfile)
	log.Println("Tun2Socks JNI library initialized - FIXED VERSION 2.0")
}