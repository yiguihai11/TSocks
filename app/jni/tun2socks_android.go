package main

import "C"

import (
	"context"
	"fmt"
	"log"
	"strconv"
	"strings"

	"github.com/xjasonlyu/tun2socks/v2/engine"
)

var (
	cancel context.CancelFunc
	running = false
)

const (
	defaultMTU     = 1500
	defaultLogLvl = "info"
)

// ProxyConfig represents the proxy configuration
type ProxyConfig struct {
	Type     string
	Host     string
	Port     int
	Username string
	Password string
}

// ToURL converts proxy configuration to URL string
func (p *ProxyConfig) ToURL() string {
	var builder strings.Builder

	switch strings.ToLower(p.Type) {
	case "http", "https":
		builder.WriteString("http://")
	case "socks5":
		builder.WriteString("socks5://")
	default:
		builder.WriteString("socks5://") // Default to SOCKS5
	}

	if p.Username != "" && p.Password != "" {
		builder.WriteString(fmt.Sprintf("%s:%s@", p.Username, p.Password))
	}

	builder.WriteString(fmt.Sprintf("%s:%d", p.Host, p.Port))
	return builder.String()
}

// TunConfig represents the TUN device configuration
type TunConfig struct {
	FileDescriptor int
	MTU           int
}

// ToDeviceString converts TUN config to device string
func (t *TunConfig) ToDeviceString() string {
	return fmt.Sprintf("fd://%d", t.FileDescriptor)
}

// Tun2SocksEngine represents the tun2socks engine wrapper
type Tun2SocksEngine struct {
	ctx       context.Context
	cancel    context.CancelFunc
	proxyCfg  *ProxyConfig
	tunCfg    *TunConfig
	logLevel  string
}

// NewTun2SocksEngine creates a new engine instance
func NewTun2SocksEngine(fd int, proxyType, server, password string, port int, excludedIps string) *Tun2SocksEngine {
	ctx, cancel := context.WithCancel(context.Background())

	return &Tun2SocksEngine{
		ctx: ctx,
		cancel: cancel,
		proxyCfg: &ProxyConfig{
			Type:     proxyType,
			Host:     server,
			Port:     port,
			Username: password,
			Password: password,
		},
		tunCfg: &TunConfig{
			FileDescriptor: fd,
			MTU:           defaultMTU,
		},
		logLevel: defaultLogLvl,
	}
}

// Start starts the tun2socks engine
func (e *Tun2SocksEngine) Start() error {
	if running {
		return fmt.Errorf("engine is already running")
	}

	// Validate configuration
	if err := e.validateConfig(); err != nil {
		return fmt.Errorf("configuration validation failed: %w", err)
	}

	// Create engine key with modern Go struct literals
	key := engine.Key{
		MTU:      e.tunCfg.MTU,
		Device:   e.tunCfg.ToDeviceString(),
		Proxy:    e.proxyCfg.ToURL(),
		LogLevel: e.logLevel,
	}

	// Insert configuration and start engine
	engine.Insert(&key)

	// Start engine in a goroutine with error handling
	go func() {
		defer func() {
			if r := recover(); r != nil {
				log.Printf("Engine panic recovered: %v", r)
				running = false
			}
		}()

		engine.Start()
		running = false
	}()

	running = true
	cancel = e.cancel
	log.Printf("Tun2Socks engine started - Device: %s, Proxy: %s",
		e.tunCfg.ToDeviceString(), e.proxyCfg.ToURL())

	return nil
}

// Stop stops the tun2socks engine
func (e *Tun2SocksEngine) Stop() error {
	if !running {
		return fmt.Errorf("engine is not running")
	}

	if e.cancel != nil {
		e.cancel()
	}

	engine.Stop()
	running = false

	log.Println("Tun2Socks engine stopped successfully")
	return nil
}

// validateConfig validates the engine configuration
func (e *Tun2SocksEngine) validateConfig() error {
	if e.tunCfg.FileDescriptor <= 0 {
		return fmt.Errorf("invalid file descriptor: %d", e.tunCfg.FileDescriptor)
	}

	if e.tunCfg.MTU <= 0 {
		return fmt.Errorf("invalid MTU: %d", e.tunCfg.MTU)
	}

	if e.proxyCfg.Host == "" {
		return fmt.Errorf("proxy host cannot be empty")
	}

	if e.proxyCfg.Port <= 0 || e.proxyCfg.Port > 65535 {
		return fmt.Errorf("invalid proxy port: %d", e.proxyCfg.Port)
	}

	return nil
}

//export Start
func Start(tunFd C.int, proxyType *C.char, server *C.char, port C.int, password *C.char, excludedIps *C.char) {
	// Convert C strings to Go strings using modern syntax
	config := NewTun2SocksEngine(
		int(tunFd),
		C.GoString(proxyType),
		C.GoString(server),
		C.GoString(password),
		int(port),
		C.GoString(excludedIps),
	)

	if err := config.Start(); err != nil {
		log.Printf("Failed to start tun2socks engine: %v", err)
	}
}

//export Stop
func Stop() {
	// Create a dummy engine to stop (since we use global state)
	engine := &Tun2SocksEngine{}
	if err := engine.Stop(); err != nil {
		log.Printf("Failed to stop tun2socks engine: %v", err)
	}
}

// main function is required for CGO to compile a shared library
func main() {
	// Initialize logging
	log.SetFlags(log.LstdFlags | log.Lshortfile)
}