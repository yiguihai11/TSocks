# TSocks Android App: Development Plan (V4 - Final)

本文件详细描述了 `TSocks` Android 应用的开发计划，该应用基于 `tun2socks` 核心库，并提供了丰富的配置选项和良好的用户体验。

### 1. 项目创建与CI/CD基础

- **包名**: `com.yiguihai.tun2socks`
- **应用名**: `TSocks`
- **构建系统**: 使用标准的 Android Gradle 项目结构。
- **CI/CD**: 整个构建流程将完全可通过命令行（`./gradlew`）驱动，方便与 GitHub Actions 等持续集成/持续部署工具链集成。CI脚本将负责执行 `gomobile` 编译和 `gradlew assembleRelease` 打包。

### 2. JNI库集成与日志系统

- **JNI库生成**: 使用 `gomobile bind` 命令将 `tun2socks` Go 库编译为 Android Archive (`.aar`) 文件，其中包含 Java 调用接口和所有CPU架构的 `.so` 动态库。
- **日志回调机制**: 为了实现日志从 Native 层到 UI 层的传递，将在 Go 中定义一个接口。Android 端将实现此接口，并将一个回调对象传递给 JNI 层。Go 代码可以通过调用此接口的方法，将日志字符串实时发送回 Java/Kotlin。

### 3. 用户界面(UI)与用户体验(UX)

应用界面将分为两个主要部分：主界面和高级设置界面。

#### 3.1. 主界面 (`MainActivity`)

- **代理配置区**:
    - `Spinner` (下拉菜单): 用于选择代理协议 (e.g., SOCKS5, Shadowsocks)。
    - `EditText` (输入框): 用于填写服务器地址、端口、密码等连接参数。
- **控制区**:
    - “启动/停止” VPN 的按钮。
    - 显示当前连接状态的 `TextView` (e.g., "已断开", "连接中...", "已连接")。
- **快捷入口**:
    - 一个“高级设置”按钮，点击后导航到 `SettingsActivity`。
- **日志显示区**:
    - 一个 `ScrollView` 包裹的 `TextView`，用于显示 `tun2socks` 核心的实时日志。
    - **智能滚动**: 当新日志产生时，若视图已在底部，则自动滚动；若用户已手动上翻，则停止自动滚动。

#### 3.2. 高级设置界面 (`SettingsActivity`)

- **网络参数**:
    - `MTU`: `EditText`，允许用户自定义 MTU 值，默认为 `1500`。
    - `DNS`: 两个 `EditText`，允许用户为 IPv4 和 IPv6 分别指定 DNS 服务器。若留空，则使用内置的公共DNS。
- **路由与IP协议**:
    - `启用 IPv4 / 启用 IPv6`: 两个 `Switch` 开关，让用户决定是否处理 v4 或 v6 的流量。
- **分流设置**:
    - **代理模式选择**: 提供“排除模式”和“包含模式”两个单选按钮。
    - **应用列表**: 一个显示手机上所有已安装应用的列表（带图标和名称）。用户可勾选应用。
        - 在 **排除模式** 下，被勾选的应用流量将**直连**，不通过VPN。
        - 在 **包含模式** 下，**只有**被勾选的应用流量会通过VPN。
    - **排除IP路由**: 一个多行 `EditText`，允许用户输入 CIDR 格式的 IP 网段，这些目标的流量将不通过VPN。

#### 3.3. 数据持久化

- 所有用户配置，包括代理设置、高级设置和应用列表，都将使用 `SharedPreferences` 进行持久化存储。

### 4. VpnService 与路由逻辑

`TSocksVpnService` 是整个应用的核心，它负责创建和管理VPN隧道。

- **配置加载**: 启动时，从 `SharedPreferences` 加载所有用户配置。
- **`VpnService.Builder` 精细化配置**:
    - `setMtu()`: 应用用户指定的 MTU。
    - `addAddress()` / `addRoute()`: 根据用户启用的 IP 协议，为虚拟网卡配置本地 IP 并添加全局路由。
    - `addDnsServer()`: 应用用户指定的 DNS 或默认 DNS。
    - **应用过滤**:
        - 如果是 **排除模式**，遍历勾选的应用列表，为每个应用调用 `builder.addDisallowedApplication(packageName)`。
        - 如果是 **包含模式**，遍历勾选的应用列表，为每个应用调用 `builder.addAllowedApplication(packageName)`。
    - `setSession()`: 为 VPN 连接设置一个明确的名称。
- **JNI 调用**:
    - `builder.establish()` 成功后，将获取到的 `ParcelFileDescriptor` 的文件描述符（整数）传递给 JNI。
    - 同时，将代理配置、日志回调对象、排除IP列表等信息一并传递给 `tun2socks` 的启动函数。
