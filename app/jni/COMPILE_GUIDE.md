# Tun2Socks Android 编译和部署指南

## 📋 前提条件

### 系统要求
- **Android NDK**: 27.3.1375024 或更高版本
- **Go**: 1.21+ (测试使用 1.25.2)
- **Android SDK**: API 26+ (目标 API 34)

### 依赖项
```go
module tun2socks_jni

go 1.25

require (
    github.com/xjasonlyu/tun2socks/v2 v2.6.0
)
```

## 🔧 编译步骤

### 1. Android 交叉编译

#### 单架构编译
```bash
# ARM64
export CGO_ENABLED=1
export GOOS=android
export GOARCH=arm64
export CC=/path/to/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang

go build -buildmode=c-shared -ldflags="-s -w" -o libtun2socks.so tun2socks_android_fixed.go

# ARM32
export GOARCH=arm
export CC=/path/to/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi26-clang

# x86
export GOARCH=386
export CC=/path/to/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/i686-linux-android26-clang

# x86_64
export GOARCH=amd64
export CC=/path/to/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android26-clang
```

#### 使用 Gradle 构建脚本
```bash
# 在项目根目录运行
./gradlew buildGoLibs

# 或者分步构建
./gradlew buildGoLib_arm64-v8a
./gradlew buildGoLib_armeabi-v7a
./gradlew buildGoLib_x86
./gradlew buildGoLib_x86_64
```

### 2. 验证编译结果

#### 检查库文件
```bash
# 检查生成的共享库
file src/main/jniLibs/arm64-v8a/libtun2socks.so
# 应该显示: ELF 64-bit LSB shared object, ARM aarch64

# 检查导出符号
nm -D src/main/jniLibs/arm64-v8a/libtun2socks.so | grep Java
```

#### 运行测试
```bash
# 编译测试程序
export CC=gcc
export CGO_ENABLED=1
go build -o test_fixed tun2socks_android_fixed.go test_fixed.go

# 运行测试
./test_fixed
```

## 📱 Android 集成

### 1. JNI 接口使用

#### Java 原生方法声明
```java
public class Tun2Socks {
    static {
        System.loadLibrary("tun2socks");
    }

    // 启动 tun2socks (推荐使用 - 支持用户名/密码分离)
    public native void Start(int tunFd, String proxyType, String server,
                           int port, String username, String password, String excludedIps);

    // 使用代理URL启动
    public native void StartWithUrl(int tunFd, String proxyUrl, String excludedIps);

    // 停止 tun2socks
    public native void Stop();

    // 获取统计信息
    public native long getStats();

    // 设置超时
    public native void setTimeout(int timeoutMs);

    // 测试JNI连接
    public native long testJNI();
}
```

#### Kotlin 使用示例
```kotlin
class VpnService : VpnService() {
    private lateinit var tun2socks: Tun2Socks
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 初始化JNI
        tun2socks = Tun2Socks()

        // 创建VPN接口
        vpnInterface = Builder()
            .setSession("Tun2Socks")
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .establish()

        vpnInterface?.let { vpn ->
            val fd = vpn.detachFd()

            // 启动tun2socks
            tun2socks.Start(
                fd,                    // TUN文件描述符
                "socks5",             // 代理类型
                "proxy.example.com",  // 代理服务器
                1080,                 // 代理端口
                "username",           // 用户名 (修复后的版本支持)
                "password",           // 密码
                "192.168.1.0/24"     // 排除的IP段
            )
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        tun2socks.Stop()
        vpnInterface?.close()
    }
}
```

### 2. 权限配置

#### AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

### 3. 错误处理

#### 异常捕获
```java
try {
    tun2socks.Start(tunFd, "socks5", "127.0.0.1", 1080, "user", "pass", "");
} catch (UnsatisfiedLinkError e) {
    Log.e("Tun2Socks", "Native library not found: " + e.getMessage());
} catch (Exception e) {
    Log.e("Tun2Socks", "Failed to start tun2socks: " + e.getMessage());
}
```

## 🔍 故障排除

### 常见编译错误

#### 1. NDK 编译器未找到
```
error: ../../../../../usr/bin/aarch64-linux-android-clang: line 3: ./clang: No such file or directory
```
**解决方案**: 设置正确的 NDK 路径
```bash
export ANDROID_NDK_HOME=/path/to/android-ndk
export PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH
```

#### 2. Go 版本兼容性
```
go: cannot find main module
```
**解决方案**: 确保在正确的目录中运行
```bash
cd app/jni  # 确保 go.mod 在此目录
go mod tidy
```

#### 3. 依赖项问题
```
module github.com/xjasonlyu/tun2socks/v2: not found
```
**解决方案**: 下载并配置 tun2socks 子模块
```bash
cd app/jni
git submodule update --init --recursive
```

### 运行时问题

#### 1. 文件描述符无效
```
invalid file descriptor: -1
```
**解决方案**: 确保 VPN 接口正确创建
```java
ParcelFileDescriptor vpn = builder.establish();
if (vpn == null) {
    Log.e("VPN", "Failed to establish VPN interface");
    return;
}
```

#### 2. 代理连接失败
```
Failed to start tun2socks engine: dial tcp: connection refused
```
**解决方案**: 验证代理服务器可达性
```bash
# 测试代理服务器连接
telnet proxy.example.com 1080
```

## 📊 性能优化

### 编译优化
```bash
# 使用链接时优化
go build -buildmode=c-shared -ldflags="-s -w -O2"

# 移除调试信息
go build -buildmode=c-shared -ldflags="-s -w"

# 减小二进制大小
go build -buildmode=c-shared -trimpath
```

### 运行时优化
```go
// 在初始化时设置日志级别
log.SetLevel(log.WarnLevel) // 减少日志输出

// 使用更高效的缓冲池
bufferPool := sync.Pool{
    New: func() interface{} {
        return make([]byte, 1500) // MTU size
    },
}
```

## 📦 部署检查清单

### 编译阶段
- [ ] Android NDK 正确配置
- [ ] 所有目标架构编译成功
- [ ] 导出符号验证通过
- [ ] 本地测试运行正常

### 集成阶段
- [ ] JNI 库正确加载
- [ ] 权限配置完整
- [ ] VPN 接口创建成功
- [ ] 错误处理机制完善

### 测试阶段
- [ ] 功能测试通过
- [ ] 性能测试满足要求
- [ ] 内存泄漏检查
- [ ] 并发安全验证

### 发布阶段
- [ ] APK 大小优化
- [ ] 代码混淆配置
- [ ] 签名和打包
- [ ] 多设备兼容性测试

## 🚀 生产环境建议

1. **监控和日志**: 实现详细的错误日志收集
2. **资源管理**: 确保 VPN 接口和原生资源正确释放
3. **用户体验**: 提供清晰的连接状态反馈
4. **安全性**: 验证所有输入参数，防止注入攻击
5. **兼容性**: 测试不同 Android 版本和设备制造商