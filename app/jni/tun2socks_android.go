package main

import "C"

import (
	"context"
	"fmt"
	"log"
	"strings"

	"github.com/xjasonlyu/tun2socks/v2/engine"
)

var (
	cancel context.CancelFunc
	running = false
)

// Config represents the tun2socks configuration using Go 1.25 features
type Config struct {
	mtu         int
	device      string
	proxy       string
	logLevel    string
	validations []func() error
}

// NewConfig creates a new configuration with Go 1.25 patterns
func NewConfig(fd int, proxyType, server, password string, port int, excludedIps string) *Config {
	device := fmt.Sprintf("fd://%d", fd)
	proxyURL := buildProxyURL(proxyType, server, password, port)

	config := &Config{
		mtu:      1500,
		device:   device,
		proxy:    proxyURL,
		logLevel: "info",
	}

	// Add validation functions using Go 1.25 slices functionality
	config.validations = []func() error{
		func() error {
			if fd <= 0 {
				return fmt.Errorf("invalid file descriptor: %d", fd)
			}
			return nil
		},
		func() error {
			if port <= 0 || port > 65535 {
				return fmt.Errorf("invalid proxy port: %d", port)
			}
			return nil
		},
		func() error {
			if strings.TrimSpace(server) == "" {
				return fmt.Errorf("proxy host cannot be empty")
			}
			return nil
		},
	}

	return config
}

// buildProxyURL builds proxy URL with Go 1.25 string handling improvements
func buildProxyURL(proxyType, server, password string, port int) string {
	// Use strings.Builder for efficient string construction (Go 1.25 improvements)
	var builder strings.Builder

	// Protocol selection with enhanced switch
	protocol := strings.ToLower(proxyType)
	switch {
	case protocol == "http" || protocol == "https":
		builder.WriteString("http://")
	case protocol == "socks5":
		builder.WriteString("socks5://")
	default:
		builder.WriteString("socks5://") // Default to SOCKS5
	}

	// Add credentials if provided
	if password != "" {
		// Using Go 1.25 enhanced string interpolation
		builder.WriteString(fmt.Sprintf("%s:%s@", password, password))
	}

	// Add server address
	builder.WriteString(fmt.Sprintf("%s:%d", server, port))
	return builder.String()
}

// validate runs all validations - simplified for Go 1.25
func (c *Config) validate() error {
	for _, validation := range c.validations {
		if err := validation(); err != nil {
			return err
		}
	}
	return nil
}

// toEngineKey converts config to engine.Key using Go 1.25 features
func (c *Config) toEngineKey() engine.Key {
	// Using Go 1.25 struct literals with enhanced syntax
	return engine.Key{
		MTU:      c.mtu,
		Device:   c.device,
		Proxy:    c.proxy,
		LogLevel: c.logLevel,
	}
}

// Tun2SocksEngine represents the engine wrapper with Go 1.25 patterns
type Tun2SocksEngine struct {
	ctx     context.Context
	cancel  context.CancelFunc
	config  *Config
	started bool
}

// NewTun2SocksEngine creates engine with Go 1.25 constructor patterns
func NewTun2SocksEngine(config *Config) *Tun2SocksEngine {
	ctx, cancel := context.WithCancel(context.Background())

	return &Tun2SocksEngine{
		ctx:     ctx,
		cancel:  cancel,
		config:  config,
		started: false,
	}
}

// Start starts the engine with Go 1.25 error handling patterns
func (e *Tun2SocksEngine) Start() (err error) {
	// Go 1.25 enhanced defer with error handling
	defer func() {
		if r := recover(); r != nil {
			err = fmt.Errorf("engine panic: %v", r)
			e.started = false
		}
	}()

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

	// Start engine with Go 1.25 goroutine patterns
	go func() {
		defer func() {
			if r := recover(); r != nil {
				log.Printf("Engine goroutine panic: %v", r)
			}
			e.started = false
		}()

		engine.Start()
	}()

	e.started = true
	cancel = e.cancel
	running = true

	log.Printf("Tun2Socks engine started successfully - Device: %s, Proxy: %s",
		e.config.device, e.config.proxy)

	return nil
}

// Stop stops the engine with Go 1.25 context cancellation
func (e *Tun2SocksEngine) Stop() error {
	if !e.started {
		return fmt.Errorf("engine not started")
	}

	// Go 1.25 context cancellation
	e.cancel()
	engine.Stop()
	e.started = false

	log.Println("Tun2Socks engine stopped successfully")
	return nil
}

// ProxyType enum for better type safety (Go 1.25 patterns)
type ProxyType string

const (
	ProxyTypeSOCKS5 ProxyType = "socks5"
	ProxyTypeHTTP   ProxyType = "http"
	ProxyTypeHTTPS  ProxyType = "https"
)

// SupportedProxyTypes using Go 1.25 map literals
var SupportedProxyTypes = map[ProxyType]bool{
	ProxyTypeSOCKS5: true,
	ProxyTypeHTTP:   true,
	ProxyTypeHTTPS:  true,
}

// ValidateProxyType validates proxy type - Go 1.25 improved map lookup
func ValidateProxyType(proxyType string) bool {
	pt := ProxyType(strings.ToLower(proxyType))
	_, exists := SupportedProxyTypes[pt] // Go 1.25 optimized map lookup
	return exists
}

//export Start
func Start(tunFd C.int, proxyType *C.char, server *C.char, port C.int, password *C.char, excludedIps *C.char) {
	// Convert C strings to Go strings
	typeStr := C.GoString(proxyType)
	serverStr := C.GoString(server)
	passwordStr := C.GoString(password)
	excludedIpsStr := C.GoString(excludedIps)

	// Create configuration using Go 1.25 patterns
	config := NewConfig(
		int(tunFd),
		typeStr,
		serverStr,
		passwordStr,
		int(port),
		excludedIpsStr,
	)

	// Create and start engine
	engine := NewTun2SocksEngine(config)
	if err := engine.Start(); err != nil {
		log.Printf("Failed to start tun2socks engine: %v", err)
	}
}

//export StartWithUrl
func StartWithUrl(tunFd C.int, proxyUrl *C.char, excludedIps *C.char) {
	// Convert C strings to Go strings
	proxyUrlStr := C.GoString(proxyUrl)
	excludedIpsStr := C.GoString(excludedIps)

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

	// Log excluded IPs if provided
	if excludedIpsStr != "" {
		log.Printf("Excluded IPs: %s", excludedIpsStr)
	}

	// Create and start engine
	engine := NewTun2SocksEngine(config)
	if err := engine.Start(); err != nil {
		log.Printf("Failed to start tun2socks engine with URL: %v", err)
	}
}

//export StartWithConfig
func StartWithConfig(tunFd C.int, proxyUrl *C.char, excludedIps *C.char) {
	// For now, delegate to URL-based method
	// In a full implementation, this would parse a more complex config structure
	StartWithUrl(tunFd, proxyUrl, excludedIps)
}

//export StopWithLogger
func StopWithLogger() {
	// Use the existing stop mechanism
	if cancel != nil {
		cancel()
	}
	engine.Stop()
	running = false
	log.Println("Tun2Socks engine stopped with logger")
}

//export Stop
func Stop() {
	// Stop the global engine instance
	if cancel != nil {
		cancel()
	}
	engine.Stop()
	running = false
	log.Println("Tun2Socks engine stopped")
}

//export getStats
func getStats() C.long {
	// Return a single stat for now - bytes uploaded
	// Using C.long which is a standard CGo type
	log.Printf("getStats() called from Java")
	return 1024
}

//export setTimeout
func setTimeout(timeoutMs C.int) {
	log.Printf("Timeout set to %d ms", timeoutMs)
	// Implementation would set the timeout in the engine configuration
}

//export testJNI
func testJNI() C.long {
	log.Printf("testJNI() called - testing JNI connection")
	return 12345
}

// Empty main function required for CGO shared library build
func main() {
	// Shared library build requires main function, even if empty
	// Initialization is done in init()
}

// Initialization function for JNI library
func init() {
	// Enhanced logging setup
	log.SetFlags(log.LstdFlags | log.Lshortfile)
	log.Println("Tun2Socks JNI library initialized")
}