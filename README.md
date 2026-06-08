# WeakNet Simulator - 弱网模拟器

## 功能
- 基于本地 VPN (VpnService) 实现，无需 Root
- 预设网络场景：2G/3G/4G/弱WiFi/地铁/高铁/断网等
- 自定义参数：延迟、抖动、丢包率、上下行带宽
- 按应用过滤：可选择仅对指定 App 生效
- 实时统计：显示上下行包数、丢包数、延迟包数

## 技术栈
- Kotlin + Jetpack Compose + Material 3
- Android VpnService (TUN 设备拦截)
- Token Bucket 限速算法
- DelayQueue 延迟注入
- Kotlin Coroutines 异步处理

## 构建
用 Android Studio 打开项目，直接 Build & Run。

最低 API：24 (Android 7.0)
目标 API：34 (Android 14)

## 项目结构
```
app/src/main/java/com/weaknet/simulator/
├── model/
│   ├── NetworkProfile.kt    # 网络场景数据模型
│   └── TrafficStats.kt      # 流量统计
├── vpn/
│   ├── WeakNetVpnService.kt # VPN 核心服务
│   ├── PacketEngine.kt      # 弱网模拟引擎
│   ├── PacketParser.kt      # IP包解析
│   ├── PacketDelayQueue.kt  # 延迟队列
│   ├── TokenBucket.kt       # 限速令牌桶
│   └── TcpSessionManager.kt # TCP 会话管理
└── ui/
    ├── MainActivity.kt       # 主 Activity
    ├── MainViewModel.kt      # ViewModel
    ├── theme/Theme.kt        # Material 3 主题
    └── screen/MainScreen.kt  # Compose UI
```
