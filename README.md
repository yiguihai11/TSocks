# TSocks - Modern VPN Client

[![Android CI Build](https://github.com/yiguihai11/TSocks/actions/workflows/build.yml/badge.svg)](https://github.com/yiguihai11/TSocks/actions/workflows/build.yml)

TSocks is a modern Android VPN client built with Go 1.25.3 and Material 3 design system. It provides comprehensive protocol support through the tun2socks library with an elegant, user-friendly interface.

## ✨ Features

### 🎨 Modern Material 3 Design
- Beautiful card-based interface following Google's Material 3 guidelines
- Smooth animations and transitions
- Dark/light theme support
- Responsive layout for all screen sizes

### 🌐 Comprehensive Protocol Support
- **SOCKS5/4** - Standard proxy protocols
- **HTTP Proxy** - HTTP/HTTPS proxy support
- **Shadowsocks** - AEAD encryption support (aes-256-gcm)
- **Relay** - Simple TCP relay
- **Direct** - Direct connection mode
- **Reject** - Block all connections

### 🚀 Performance & Architecture
- **Go 1.25.3** - Latest Go language with modern syntax features
- **Native Performance** - Compiled to native ARM/x86 code
- **Multi-architecture** - Support for arm64-v8a, armeabi-v7a, x86, x86_64
- **Material 3** - Modern UI components and theming

### 📊 Real-time Statistics
- Connection status monitoring
- Data transfer statistics (upload/download)
- Active connection tracking
- Comprehensive logging system

### ⚙️ Advanced Configuration
- Flexible proxy configuration
- IP exclusion lists
- Connection timeout settings
- Auto-connect on startup
- System proxy configuration

## 🏗️ Technical Architecture

### Core Components

1. **Go Native Engine** (`app/jni/tun2socks_android.go`)
   - Uses tun2socks v2.6.0 library
   - Modern Go 1.25.3 language features
   - File descriptor-based device mode
   - Context-based lifecycle management

2. **Java JNI Interface** (`app/src/main/java/com/yiguihai/tun2socks/Tun2Socks.java`)
   - Comprehensive protocol enumeration
   - Multiple JNI method signatures for compatibility
   - Connection listener callbacks
   - Statistics and timeout management

3. **Material 3 UI** (`app/src/main/res/layout/activity_main.xml`)
   - CoordinatorLayout with proper elevation
   - MaterialCardView components
   - FloatingActionButton actions
   - NestedScrollView for content

### Build System

- **Gradle 9.2.0** with Android Gradle Plugin 8.2.0
- **Multi-architecture Go compilation** with proper ABI handling
- **AndroidX migration** for modern Android support
- **GitHub Actions CI/CD** for automated builds

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Arctic Fox or later
- **Go 1.25.3** or later
- **Android SDK** API 24+ (Android 7.0)
- **NDK** for native compilation

### Building the Project

1. **Clone the repository**
   ```bash
   git clone https://github.com/yiguihai11/TSocks.git
   cd TSocks
   ```

2. **Set up Go environment**
   ```bash
   cd app/jni
   go mod tidy
   ```

3. **Build with Gradle**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Install on device**
   ```bash
   ./gradlew installDebug
   ```

## 📱 Usage

1. **Configure Proxy**
   - Open TSocks app
   - Tap "Settings" button
   - Enter proxy server details
   - Select protocol type
   - Save configuration

2. **Connect to VPN**
   - Tap "Connect" button
   - Grant VPN permission when prompted
   - Monitor connection status

3. **View Statistics**
   - Real-time data transfer stats
   - Connection status indicators
   - Detailed logging output

## 🔧 Configuration

### Proxy Configuration Examples

**SOCKS5 Proxy:**
```
Protocol: SOCKS5
Server: proxy.example.com
Port: 1080
Username: (optional)
Password: (optional)
```

**HTTP Proxy:**
```
Protocol: HTTP
Server: proxy.example.com
Port: 8080
Username: (optional)
Password: (optional)
```

**Shadowsocks:**
```
Protocol: Shadowsocks
Server: ss.example.com
Port: 8388
Password: your-password
Method: aes-256-gcm
```

### Advanced Settings

- **Connection Timeout**: Configure connection timeout (default: 5000ms)
- **Excluded IPs**: Comma-separated list of IPs to bypass proxy
- **Auto-connect**: Start VPN connection automatically on app launch
- **System Proxy**: Configure system-wide proxy settings

## 🛠️ Development

### Project Structure

```
TSocks/
├── app/
│   ├── build.gradle                 # Main build configuration
│   ├── src/main/
│   │   ├── java/com/yiguihai/tun2socks/
│   │   │   ├── Tun2Socks.java      # JNI interface
│   │   │   ├── MainActivity.java   # Main activity
│   │   │   └── ...
│   │   ├── res/
│   │   │   ├── layout/             # Material 3 layouts
│   │   │   ├── values/             # Strings and themes
│   │   │   └── drawable/           # Icons and graphics
│   │   └── jniLibs/                # Compiled native libraries
│   └── jni/
│       ├── tun2socks_android.go    # Go JNI wrapper
│       ├── go.mod                  # Go module definition
│       └── go.sum                  # Go dependencies
├── gradle/                         # Gradle wrapper
├── .github/workflows/              # CI/CD workflows
└── README.md                       # This file
```

### Go Code Features

The Go native code utilizes modern Go 1.25.3 features:

- **Enhanced slices and maps** with improved syntax
- **strings.Builder** for efficient string construction
- **Context-based cancellation** for proper lifecycle management
- **Structured error handling** with Go 1.25 patterns
- **Modern goroutine patterns** for concurrent operations

### JNI Interface

The project provides both legacy and enhanced JNI methods:

```go
// Legacy compatibility method
func Start(tunFd C.int, proxyType *C.char, server *C.char, port C.int, password *C.char, excludedIps *C.char)

// Enhanced methods
func StartWithUrl(tunFd C.int, proxyUrl *C.char, excludedIps *C.char)
func StartWithConfig(tunFd C.int, proxyUrl *C.char, excludedIps *C.char)
```

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **[tun2socks](https://github.com/xjasonlyu/tun2socks)** - Core proxy library
- **[Material 3](https://m3.material.io/)** - Design system guidelines
- **[Android Jetpack](https://developer.android.com/jetpack)** - Modern Android development
- **[Go 1.25.3](https://go.dev/)** - High-performance native code

## 📞 Support

For support, please open an issue on GitHub or contact the maintainers.

---

**TSocks** - Modern VPN connectivity with Material 3 elegance 🚀