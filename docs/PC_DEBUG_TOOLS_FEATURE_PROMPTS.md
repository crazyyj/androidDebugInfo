# PC Debug Tools 功能扩展 — 提示词

> 本文档面向 AI 编程助手，包含三个功能的开发提示词。每个功能均标注了**实现端（PC / 设备）**、**现有可复用代码**、**需新建的部分**以及**验收标准**。实施前请先阅读「项目背景与全局约定」，建立准确的心智模型。

---

## 项目背景与全局约定（必读）

### 1. 双端架构

| 端 | 模块 | 技术栈 | 职责 |
|---|---|---|---|
| **设备端** | `lib_debug`（聚合库，无独立 src，编译时合并下列子模块源码）+ `lib_debug_core` / `lib_debug_log` / `lib_debug_device` / `lib_debug_touch` / `lib_debug_monitor` / `lib_debug_net` | Java + Android | 在宿主 App 中提供浮窗调试面板，含日志、设备信息、触摸录制、网络监控、PC 通信等插件 |
| **PC 端** | `app_pc_debug_tools` | Kotlin Multiplatform (JVM) + Compose Desktop | 桌面调试工具，直接执行 adb 命令，管理设备、收集日志、接收 App 上行消息 |

> 注意：`lib_debug` 自身没有 `src/`，其 `build.gradle` 通过 `sourceSets.main.java.srcDirs` 合并各子模块源码。消费者只依赖 `lib_debug` 一个 artifact。

### 2. 设备端插件体系

- **插件基类**：`ScreenDisplayPlugin`（`lib_debug_core/src/main/java/com/newchar/debug/api/ScreenDisplayPlugin.java`），抽象方法 `id() / getName() / onLoad() / onShow() / onHide() / onUnload()`。
- **注册**：`DebugManager.initialize()` 中通过 `PluginManager.registerOnce(tag, PluginClass.class)` 反射注册。已注册插件：`LogViewPlugin`、`PCToolsPlugin`、`PageUITopPlugin`、`MethodFieldMonitorPlugin`、`TouchRestorePlugin`、`DebugNetPlugin`、`DevicesInfoPlugin`、`DiskInfoPlugin`、`DebugWebPlugin`。
- **显示容器**：`DebugView`（`lib_debug_core/.../view/DebugView.java`）以 `ListPopupWindow` 在各插件间切换；插件视图被 add 进容器。浮窗由 `FloatViewService` + `CanFlowState`（`TYPE_APPLICATION_OVERLAY`）承载，无悬浮窗权限时降级到 `DebugPanelActivity`。
- **路由模块**：`lib_debug_core/.../router/`（`ResultProxyRouter` / `ResultProxyActivity` / `ResultProxyManager`）解决插件内无法 `startActivityForResult` 的问题，`DebugNetPlugin` 已用它做证书选择。

### 3. PC ↔ 设备通信通道（核心，务必理解）

```
设备 App (PCToolsPlugin)                PC (HeartbeatManager)
  Socket("127.0.0.1", 6666) ──adb reverse──▶ ServerSocket(:6666)
        writeUTF("MESSAGE|deviceId|content")        readUTF()
                                          ◀──── writeUTF("ACK")
```

- **adb reverse 由 PC 端建立**：`HeartbeatManager.setupAdbReverse(device)` 执行 `adb -s <id> reverse tcp:6666 tcp:6666`，USB 设备上线时自动设置。
- **设备端** `PCToolsPlugin`（`lib_debug_core/src/main/java/com/newchar/debug/plugin/PCToolsPlugin.java`）连接 `127.0.0.1:6666`，发送 `MESSAGE|deviceId|content`（UTF），等待 PC 回 `ACK`。
- **PC 端** `HeartbeatManager`（`app_pc_debug_tools/src/commonMain/kotlin/com/newchar/debug/pc/device/HeartbeatManager.kt`）的 `ServerSocket(6666)` 接收并解析 `HEARTBEAT|...` / `ONLINE|...` / `MESSAGE|...`，消息经 `_messages: SharedFlow<PcMessage>` 暴露给 UI。
- **设备端 `DebugUtils.getDeviceId()`** 提供 deviceId。

### 4. 触摸系统现状

- **录制链路**：`TouchRestorePlugin`(UI 按钮) → `MotionManager.start()/stop()`（`DefaultActivityCallback`，遍历所有 Activity）→ `RecordTouchEventTask.install(activity)`（用 JDK `Proxy` 包装 `Window.Callback`，拦截 `dispatchTouchEvent`）→ `MotionEvent.obtain` 拷贝 → 异步 Handler → `MotionEventSerializer.TouchRecordWriter.writeEvent()` 写入 `.mtes` 文件。
- **数据模型**：`TouchEventRecord { MotionEvent motionEvent; long timestamp; int action; }`；文件头 `TouchRecordHeader { long recordStartTime; String activityName; int eventCount; }`。MotionEvent 以 `writeToParcel → marshall` 整体存储（坐标、时间、压力、指针全保留）。
- **⚠️ 无任何 adb 触摸注入**：全工程无 `input tap` / `input swipe` / `sendevent` / `Runtime.exec` / `ProcessBuilder`。`MotionExecute` 是脚手架（方法体注释掉），仅含 in-process `dispatchTouchEvent`。功能二所需的 adb 注入需**新建**。

### 5. 屏幕画面获取现状

- 仅 `lib_debug_touch` 的 `ScreenRecordService`（MediaProjection + MediaRecorder → MP4），是**录像**，非逐帧抓取、非实时流。
- **无 scrcpy 式 H264 实时流、无 `screencap` PNG 拉取**。功能一所需实时预览需**新建**。

### 6. PC 端 adb 执行能力（功能一、二在此端实现）

- `AdbCommandExecutor`（`app_pc_debug_tools/src/jvmMain/kotlin/com/newchar/debug/pc/executor/AdbCommandExecutor.kt`）：基于 `ProcessBuilder`，提供 `adb(vararg args)`、`adbSync`、`adbStream`、`shell(deviceId, command)`（构造 `adb -s <id> shell ...`）、设备追踪流。adb 路径由 `AdbExecutableResolver` 解析。
- UI：`AppContent.kt`（三栏布局：功能导航 56dp | 设备列表 260dp | 内容区），`NavItem { DEVICES, LOGCAT }`。对话框/按钮/卡片等组件见 `CommonComponents.kt`、`AppContent.kt` 内的 `AppDialog`/`AppOutlinedButton`/`PanelCard` 等。

### 7. 通用实现约定

- 设备端用 **Java**，PC 端用 **Kotlin**；命名、注释风格与现有一致。
- 设备端复用 `lib_debug_core` 现有工具：`DebugUtils`（设备号、通知、悬浮窗权限、屏幕尺寸）、`HandleWrapper`（异步/主线程 Handler）、`AppLifecycleManager` / `DefaultActivityCallback`、`UIUtils`（屏幕/状态栏/导航栏尺寸）、`ViewUtils`、`MoveTouchListener`（浮窗拖拽）。**禁止重复造轮子**。
- 新建模块遵循 `lib_debug_*` 结构（`build.gradle` 依赖 `lib_debug_core`，独立 `AndroidManifest.xml`）。

---

## 功能一：设备实时预览（adb 录屏，独立窗口）

### 目标
在 PC 端为某设备提供「预览」入口，点击后弹出**独立窗口**实时显示设备画面：
- 显示比例与设备真实分辨率一致（不拉伸变形）。
- 设备方向（横竖屏 / 旋转）切换时，预览窗口**同步切换**。
- 不阻塞 PC 主界面。

### 实现端
**PC 端**（`app_pc_debug_tools`）。adb 在 PC 执行，**不修改设备端代码**。

### 涉及与新建
| 类型 | 位置 |
|---|---|
| 复用 | `AdbCommandExecutor`（执行 adb 命令）、`AppContent.kt` 内 `AppDialog`/`AppOutlinedButton`/`PanelCard` 等组件 |
| 新建 | 预览窗口 Composable（建议 `app_pc_debug_tools/src/jvmMain/kotlin/com/newchar/debug/pc/device/preview/DevicePreviewWindow.kt`）、帧获取与渲染逻辑 |

### 实现要点
1. **帧获取方案（需评估并选择，给出取舍）**
   - 方案 A（简单、低频）：`adb exec-out -s <id> screencap -p` 拉取 PNG 流，定时（如 100~200ms）刷新显示。优点：实现简单；缺点：帧率低、CPU/IO 高。
   - 方案 B（推荐、流畅）：基于 H264 流 —— `adb exec-out screenrecord --output-format=h264 -` 管道输出，PC 端解码（可用 Java 原生解码或集成轻量 H264 解码库）。或评估引入 scrcpy 协议（`adb forward` + scrcpy server jar）。
   - 请在文档/代码注释中说明所选方案及理由。
2. **分辨率与比例**
   - `adb -s <id> shell wm size` 取真实分辨率（如 `Physical size: 1080x2400`）。
   - 预览窗口按该比例约束宽高（Compose 中用 `aspectRatio` 或固定比例的 `Box`）。
3. **方向同步**
   - `adb -s <id> shell settings get system user_rotation` 或 `dumpsys SurfaceFlinger | grep -i rotation` / `dumpsys input | grep -i orientation` 取当前旋转角度。
   - 轮询（如 500ms）检测变化，旋转预览画面（`Modifier.graphicsLayer { rotationZ = ... }`）或调整窗口比例。
4. **独立窗口**
   - Compose Desktop 用 `androidx.compose.ui.window.Window`（独立 OS 窗口）或现有 `Dialog` 风格。要求可与主界面并存、可移动/缩放/关闭。
   - 关闭时务必取消 adb 流读取协程，避免泄漏。
5. **入口**：在 `DeviceDetailPanel`（`AppContent.kt`）设备详情区新增「预览」按钮。

### 验收标准
- [ ] 点击「预览」弹出独立窗口，画面实时跟随设备。
- [ ] 画面比例 = 设备真实分辨率比例，无变形。
- [ ] 设备旋转后预览窗口在 ≤1s 内同步切换方向。
- [ ] 预览窗口与主界面互不阻塞，关闭预览不泄漏 adb 进程/协程。
- [ ] adb 不可用/设备离线时给出明确提示而非崩溃。

---

## 功能二：预览画面触摸透传（adb input + move 事件过滤）

### 目标
在功能一的预览窗口上用鼠标（PC）/触摸操作，将操作**透传**到设备：
- 点击 → 设备对应坐标 tap。
- 拖拽 → 设备对应坐标 swipe。
- **move 事件做简单过滤**，减少 adb 命令数量（每次 `input` 命令都会启动进程，高频 move 会刷屏/延迟）。

### 实现端
**PC 端**（`app_pc_debug_tools`）。通过 adb 注入，**不修改设备端代码**。

### 涉计与新建
| 类型 | 位置 |
|---|---|
| 复用 | `AdbCommandExecutor.shell(deviceId, ...)` 或 `adb(...)`、功能一新建的预览窗口组件 |
| 新建 | 坐标映射器、move 过滤器、输入注入调用 |

### 实现要点
1. **注入命令**
   - 点击：`adb -s <id> shell input tap <x> <y>`
   - 拖拽：`adb -s <id> shell input swipe <x1> <y1> <x2> <y2> <durationMs>`
   - ⚠️ 每次 `input` 起一个进程，**禁止逐点发送**。
2. **move 过滤策略（简单但有效，按需实现其一或组合）**
   - **时间间隔阈值**：相邻两点时间差 ≥ 40ms 才采样发送。
   - **位移阈值**：相邻两点距离 ≥ 5px（设备坐标）才采样。
   - **抬手补点**：ACTION_UP 时用真实终点补发一次 `swipe`（起点=上次采样点，终点=up 点，duration=实际耗时），保证落点准确。
   - 建议实现成一个 `TouchFilter`，输入原始 Pointer 事件流，输出稀疏化的注入命令序列。
3. **坐标映射**
   - 窗口像素坐标 → 设备坐标：按窗口显示尺寸与设备分辨率等比换算。
   - **旋转处理**：设备旋转 90°/180°/270° 时，窗口坐标需做对应矩阵变换后再映射到设备坐标（x/y 互换 + 翻转）。
4. **交互约束**
   - 预览窗口捕获鼠标按下/移动/抬起（Compose `pointerInput { detectDragGestures ... }` 或 `awaitPointerEventScope`）。
   - 区分单击（down/up 位移小）与拖拽（位移大）。
5. **性能**：move 命令串行化执行（单设备同一时刻一条 `input`），用队列 + 协程消费，避免命令堆积。

### ⚠️ 重要边界
当前 Android 侧**无任何 Runtime.exec / 触摸注入代码**，本功能完全在 PC 端用 adb 完成，不要新增设备端注入逻辑。

### 验收标准
- [ ] 在预览窗口点击，设备对应坐标触发 tap。
- [ ] 拖拽时设备触发 swipe，落点准确。
- [ ] move 过滤后 adb 命令频率明显下降，且操作轨迹基本连贯、不丢关键点。
- [ ] 设备旋转后坐标映射仍正确。
- [ ] 鼠标抬起后无残留拖拽状态。

---

## 功能三：PCToolsPlugin 与 VPN 解耦（解决插件切换/通信干扰）

### 问题根因（务必先理解）
`DebugNetVpnService`（`lib_debug_net/src/main/java/com/newchar/debug/net/DebugNetVpnService.java`）构建 TUN 接口时调用：
```java
builder.addAddress("10.88.0.2", 32);
builder.addRoute("0.0.0.0", 0);          // 拦截所有 IPv4 流量
builder.addAllowedApplication(getPackageName());  // 只捕获宿主 App 自身流量
```
而 `PCToolsPlugin` 发往 `127.0.0.1:6666`（adb reverse 通道）的 socket 流量**同样属于宿主 App 的出站连接**，被 VPN TUN 接管、送入 `IpPacketParser` / `TcpSessionTable` / `PacketForwarder` 解析链路。结果：
1. PCToolsPlugin 的消息可能被 VPN 当作 HTTP 流量解析/转发，送达 PC 异常。
2. `DebugNetPlugin`（VPN 监控面板）与 `PCToolsPlugin`（PC 通信面板）在 `DebugView` 中切换显示时，因共用网络出口而互相干扰、显示错乱。

> 注意：环路地址 `127.0.0.1` 通常不经 VPN 路由，但 `adb reverse` 转发涉及 adbd 进程在设备侧的 socket 中继，实际行为与 Android 版本/VPN 实现相关。请 AI 先用日志确认 PCToolsPlugin 流量是否真的进入 VPN capture loop，再决定改动范围。

### 目标
- VPN 开启时，`PCToolsPlugin` 的消息仍能正常送达 PC（不被 VPN 干扰或被正确放行）。
- `DebugNetPlugin` 与 `PCToolsPlugin` 在 `DebugView` 中切换时，**各自独立显示、互不串扰**。

### 涉及文件
| 文件 | 说明 |
|---|---|
| `lib_debug_core/src/main/java/com/newchar/debug/plugin/PCToolsPlugin.java` | PC 通信插件，连 `127.0.0.1:6666` 发 `MESSAGE\|deviceId\|content` |
| `lib_debug_net/src/main/java/com/newchar/debug/net/DebugNetVpnService.java` | VPN 服务，`addRoute("0.0.0.0",0)` + `addAllowedApplication(getPackageName())` |
| `lib_debug_net/src/main/java/com/newchar/debug/net/IpPacketParser.java` | IP/TCP 解析，产出 `RawPacket` |
| `lib_debug_net/src/main/java/com/newchar/debug/net/PacketForwarder.java` | 真实 socket 中继 + TUN 回写 |
| `lib_debug_net/src/main/java/com/newchar/debug/net/TcpSessionTable.java` / `TcpSession.java` | HTTP 重组，可能误解析 6666 流量 |
| `lib_debug_net/src/main/java/com/newchar/debug/plugin/DebugNetPlugin.java` | VPN 监控 UI，与 PCToolsPlugin 同在 DebugView |

### 实现要点（两条路径，请对比后选择侵入最小者）

**路径 A：VPN 侧放行 PCToolsPlugin 通道（推荐，改动集中）**
1. 在 `DebugNetVpnService` 的 `captureLoop` / `IpPacketParser` 解析阶段，识别目标是本地 `6666` 端口（或回路地址）的包，**直接跳过**解析与转发，交回系统默认网络栈。
   - 可在 `IpPacketParser.parse` 产出 `RawPacket` 后，按 `dstPort == 6666 && dstIp is loopback` 过滤。
2. 或在 `VpnService.Builder` 上调整路由，使本地端口不走 TUN（注意 `0.0.0.0/0` 路由的优先级；Android 对 loopback 有特殊处理，需实测）。
3. 或对 PCToolsPlugin 的 socket 调用 `vpnService.protect(socket)`，使其绕过 VPN（需 `VpnService` 暴露 protect 能力给 PCToolsPlugin，可经 `VpnServiceHolder` 持有的引用）。

**路径 B：PCToolsPlugin 通信通道独立化**
1. 让 PCToolsPlugin 不再走可能被 VPN 拦截的网络栈，例如改用独立线程 + 被 protect 的 socket，或显式绑定到非 VPN 接口。

**插件切换显示问题**
- 检查 `DebugView` 切换插件时，两插件的 `onShow/onHide` 是否有共享状态（如同一 Handler、同一 socket 池）导致显示串扰。
- 确认 `PCToolsPlugin` 的 `mHandler`、连接检测 `Runnable` 与 `DebugNetPlugin` 的事件批处理（`mPendingEvents` + `mFlushTask`）彼此隔离。

### 验收标准
- [ ] 开启 VPN（`DebugNetPlugin` 启动 `DebugNetVpnService`）时，`PCToolsPlugin` 发送消息仍能正常送达 PC 并收到 `ACK`。
- [ ] VPN 监控列表中**不应出现** PCToolsPlugin 到 `6666` 的连接记录（或被正确归类/过滤）。
- [ ] 在 `DebugView` 中反复切换「PC 通信」与「网络监控」插件，两者各自独立显示、数据不串扰、UI 不残留。
- [ ] 关闭 VPN 后 PCToolsPlugin 行为不变。
