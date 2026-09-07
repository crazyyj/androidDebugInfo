# 桌面端预览功能改造 — 提示词

> 本文档面向 AI 编程助手，对象是 `app_pc_debug_tools`（PC 端，Kotlin Multiplatform + Compose Desktop）的设备实时预览窗口。改造分为五块：①App↔PC 通信连通性检查、②touch 模块录屏增加实时流、③adb screenrecord 解码、④PNG 流动态降频、⑤预览窗口置顶。实施前务必读「现状」一节。

---

## 一、现状（务必先理解，避免重复造轮子）

### 1. 预览窗口 `DevicePreviewWindow.kt`
路径：`app_pc_debug_tools/src/jvmMain/kotlin/com/newchar/debug/pc/device/preview/DevicePreviewWindow.kt`。已实现：
- **PNG 帧源** `AdbScreenCapture`：`ProcessBuilder(executor.resolveAdbCommand("-s", deviceId, "exec-out", "screencap", "-p"))` 拉取一帧 PNG → `org.jetbrains.skia.Image.makeFromEncoded` → `asImageBitmap()`。
- **固定拉帧节奏**：`FRAME_INTERVAL_MS = 150L`（约 6fps），在 `LaunchedEffect(capture)` 的 `while (isActive)` 循环里 `delay(150)`。
- **设备屏幕参数** `DeviceDisplayInfo(naturalWidth, naturalHeight, rotation)` + `queryDisplayInfo()`（`wm size` 取分辨率、`settings get system user_rotation` 取旋转），轮询 `DISPLAY_POLL_INTERVAL_MS = 500L`。
- **坐标映射** `PreviewCoordinateMapper`：预览窗口像素 → 设备自然坐标（含 90/180/270 旋转逆变换）。
- **触摸透传** `TouchFilter`（移动 ≥5px 且间隔 ≥40ms 才出 swipe、抬手补点）+ `AdbInputInjector`（Channel(capacity=1, DROP_OLDEST) 串行执行 `input tap`/`input swipe`）。
- **窗口**：`androidx.compose.ui.window.Window`，`rememberWindowState(420dp, 760dp)`，标题「预览 - <model>」，顶部状态条显示分辨率/旋转/「PNG 低频预览」。

### 2. App↔PC 通信通道（需求 1 涉及）
- **通道**：`adb reverse tcp:6666 tcp:6666`，由 PC 端 `HeartbeatManager.setupAdbReverse(device)` 在 USB 设备上线时自动建立（`app_pc_debug_tools/src/commonMain/kotlin/com/newchar/debug/pc/device/HeartbeatManager.kt`）。
- **PC 端**：`HeartbeatManager.startReverseServer()` 起 `ServerSocket(6666)`，`handleClient` 解析 `HEARTBEAT|deviceId|ts` / `ONLINE|...` / `MESSAGE|...`，回 `ACK`。
- **设备端** `PCToolsPlugin`（`lib_debug_core/.../plugin/PCToolsPlugin.java`）连 `127.0.0.1:6666` 发 `MESSAGE|deviceId|content`。
- **当前预览窗口未做此通道的连通性检查** —— 用户无法直观知道 App→PC 的反向通道是否打通。

### 3. touch 模块录屏（需求 2 涉及）
- 路径：`lib_debug_touch/src/main/java/com/newchar/debug/touch/` 下 `ScreenRecordManager`（静态 facade）/ `ScreenRecordService`（前台服务）/ `ScreenRecordPermissionActivity`（透明 Activity 申请 MediaProjection）。
- **现状是「录像到 MP4 文件」**：`MediaProjection.createVirtualDisplay(...)` + `MediaRecorder`（H264/30fps/6Mbps）输出 `<externalCacheDir>/.v/screen-<ts>.mp4`。**没有把 H264 编码数据实时推到外部（PC）的逻辑，无实时流。**
- 由 `TouchRestorePlugin`（`lib_debug_touch/.../plugin/TouchRestorePlugin.java`）的「开始/暂停/停止录屏」按钮触发。

### 4. adb screenrecord 能力（需求 3 涉及）
- 设备自带 `screenrecord` 命令支持 `--output-format=h264`，可用 `adb exec-out -s <id> screenrecord --output-format=h264 -` 把 H264 裸流通过 stdout 管道输出到 PC。
- **当前预览只用 PNG（`screencap`），未使用 screenrecord 的 H264 流**。需求 3 要新增 H264 解码渲染路径。

### 5. 依赖现状
- `build.gradle.kts` `commonMain` 已有：`kotlinx-coroutines-core`、`kotlinx-serialization-json`、`kotlinx-io-core`、compose runtime/foundation。`jvmMain` 有 compose desktop currentOs。
- 图像解码现用 **Skia**（`org.jetbrains.skia.Image.makeFromEncoded`，PNG/JPEG 内置）。**H264 解码需新增依赖**（见需求 3 决策点）。
- ⚠️ 是否引入 H264 解码库是关键决策点，文档第二节给出方案对比。

---

## 二、需求与决策点

### 需求 1：App↔PC（6666 reverse 通道）连通性检查
在预览窗口**直观显示**「设备 App 是否能与 PC 通信」，并据此给用户排障提示。

### 需求 2：touch 模块录屏增加实时流（与需求 3 并存）
改造 `lib_debug_touch` 的 `ScreenRecordService`，把 MediaProjection 编码出的 H264 **实时推流**到 PC（而非只存 MP4），作为预览的「App 端推流」路径，与需求 3 的「adb 端拉流」并存。

### 需求 3：adb shell screenrecord 录制 + PC 端解码
用 `adb exec-out screenrecord --output-format=h264 -` 拉 H264 裸流，PC 端解码渲染，作为「adb 端拉流」路径（不依赖 App，独立可用）。

### 需求 4：PNG 流动态降频
保留 PNG 方案，但当用户在预览窗口**正在拖拽（move）**时，把截图间隔从 `150ms` 放宽到 `15s`，并**降低单帧 PNG 质量**（保持宽高比，即缩小尺寸/降低分辨率，`screencap` 不支持压缩参数，通过缩小尺寸实现降质）。

### 需求 5：预览窗口置顶
预览的独立窗口默认**置于屏幕最顶**（always-on-top），并提供开关可关闭置顶。

> ⚠️ 决策点（已在下文标注，实施时按推荐项执行）：
> - 需求 1：连通性检查的实现粒度（推荐「PC 端主动探测 + 复用 HeartbeatManager 状态」）。
> - 需求 3：H264 解码库选型（推荐「javacv/ffmpeg」或「评估 scrcpy 协议」）。
> - 需求 4：「move 中」的判定来源（推荐复用 `TouchFilter`/`pointerInput` 手势状态）。

---

## 三、实现要点（逐项落到代码层面）

### 3.1 需求 1 — 6666 reverse 通道连通性检查

**判定语义**：reverse 通道是否打通 = 设备 App（PCToolsPlugin）能否把消息经 `adb reverse :6666` 送到 PC 端 `HeartbeatManager` 的 `ServerSocket(6666)`。

**实现路径（推荐：复用现有状态，不新开端口探测）**：
1. `HeartbeatManager` 已维护 `lastHeartbeatAt: Map<String, Long>`（按 deviceId 记录最近一次收到 App 消息/心跳的时间）。将其暴露为可读状态（如新增 `fun lastContact(deviceId): Long`，或把 `lastHeartbeatAt` 包成 `StateFlow<Map<String,Long>>`）。
2. 在预览窗口新增「通信状态」指示：
   - 取该设备的 `lastContact(deviceId)`，若 `now - lastContact ≤ 阈值`（建议取 `HeartbeatManager` 心跳间隔的 ~1.5 倍，或 60s），判定为「已联通」，否则「未联通」。
   - UI 顶部状态条加一个圆点+文字（绿=已联通 / 灰=未联通），鼠标悬停显示「最近通信时间 mm:ss」。
3. **主动探测兜底**（可选，更准确）：PC 端无法直接向 reverse 通道发请求（reverse 是设备→PC 方向），但可在设备 App 端加一个「收到 PING 立即回 ONLINE」的轻量协议，PC 触发一次设备端 `am broadcast` 让 App 回送一条消息，用以主动验证。若改动需触及 App，请标注为可选增强，默认用被动心跳状态判定。
4. **未联通时的排障提示**：状态条点开/悬停显示「请确认：①USB 调试在线 ②`adb reverse tcp:6666 tcp:6666` 已建立 ③设备 App(PCToolsPlugin) 已运行」。

**涉及文件**：`HeartbeatManager.kt`（暴露状态）、`DevicePreviewWindow.kt`（状态条 UI + 指示器）。

### 3.2 需求 2 — touch 模块录屏实时流（App 端推流路径）

**目标**：`ScreenRecordService` 在录 MP4 的同时，把 H264 编码帧实时推送到 PC（并存档的 MP4 行为不破坏）。这条路径与需求 3 的 adb 拉流并存，作为「App 主动推流」的备选/增强。

**实现要点**：
1. **传输通道**：复用 6666 reverse 通道或新增独立端口（如 `adb reverse tcp:6667 tcp:6667`）。建议新增独立端口传输二进制 H264，避免与现有 `MESSAGE|...` 文本协议混在同一 socket。
   - 设备端 `ScreenRecordService` 起 `Socket("127.0.0.1", 6667)` 连接（经 reverse 转发到 PC）。
   - PC 端新增 `ServerSocket(6667)` 接收，与 HeartbeatManager 的 reverse server 同模式。
2. **MediaRecorder → 实时帧**：当前用 `MediaRecorder` 输出 MP4 文件，无法直接拿裸 H264 帧。改造方案二选一：
   - **方案 A（轻改，推荐先做）**：保留 MediaRecorder 输出 MP4，额外对编码后的 MP4 文件做分段（如每 N 秒切一个文件）并 `adb pull` 到 PC 解码播放 —— 本质是「准实时」，延迟较高。
   - **方案 B（真·实时流，推荐目标态）**：把编码器从 `MediaRecorder` 换成 `MediaCodec`（配置为 H264 encoder），`VirtualDisplay` 输出到 `MediaCodec` 的 input Surface，循环 `dequeueOutputBuffer` 取出编码后的 H264 NAL，经 socket 边界协议（如 `[len(4)][h264 bytes]` 帧分帧）推送给 PC。同时若仍需 MP4，用 `MediaMuxer` 在设备端落盘。
   - 文档明确：需求 2 的「实时流」应实现到方案 B 才算达标；方案 A 仅作过渡。
3. **协议**：自定义轻量帧协议，建议 `[magic=4B][len=4B][pts=8B][h264 payload]`，PC 端按帧解析喂给解码器。
4. **生命周期**：`TouchRestorePlugin` 新增「实时推流」开关（与现有「录屏开始」并列），停止时关闭 socket 并 flush 编码器。

**涉及文件**：`lib_debug_touch/.../ScreenRecordService.java`（改编码与推流）、`ScreenRecordManager.java`（facade 增加 start/stop stream）、`TouchRestorePlugin.java`（UI 开关）、PC 端新增 `app_pc_debug_tools/.../device/preview/AppStreamServer.kt`（接 6667）与解码消费。

### 3.3 需求 3 — adb screenrecord 录制 + PC 端解码（adb 端拉流路径）

**目标**：不依赖 App，PC 直接用 `adb exec-out -s <id> screenrecord --output-format=h264 -` 拉 H264 裸流，PC 端解码渲染。

**实现要点**：
1. **拉流**：新增帧源 `AdbScreenRecordStream`（与现有 `AdbScreenCapture` 并列），用 `ProcessBuilder(executor.resolveAdbCommand("-s", deviceId, "exec-out", "screenrecord", "--output-format=h264", "-"))` 启动长驻进程，**持续读 stdout**（不是等进程结束）。注意 `screenrecord` 默认 180s/3 分钟超时上限，需要 `--time-limit` 调大或在超时后自动重启进程（每次重启会有一帧 IDR 重新同步）。
2. **H264 解码（⚠️ 关键决策）**：Skia 的 `Image.makeFromEncoded` 只能解 PNG/JPEG/WebP 等静态图，**不能解 H264**。需引入解码能力，方案对比：
   - **方案 A：JavaCV（FFmpeg 封装）** — `org.bytedeco:javacv-platform`。`FFmpegFrameGrabber` 直接喂 H264 字节流出 `Frame`/`BufferedImage`。优点：跨平台、成熟、API 简单。缺点：依赖体积大（数百 MB，含各平台 native 库）。**推荐**用于桌面端，体积可接受。
   - **方案 B：scrcpy 协议** — 引入 scrcpy server jar 推到设备 + `adb forward`，PC 端按 scrcpy 协议解析 H264 + control 事件。优点：生态成熟、延迟低、还能复用其触摸注入。缺点：实现复杂、需打包 server jar、协议跟随 scrcpy 版本。**不推荐作为第一版**，可作为后续优化。
   - **方案 C：纯 JDK** — JDK 无内置 H264 解码（无 javax.media）。排除。
   - 文档要求：第一版用方案 A（JavaCV），在代码注释说明选型理由，并把方案 B 列为后续可演进项。
3. **渲染对接 Compose**：JavaCV 解出 `BufferedImage` → 转 `ImageBitmap`（`org.jetbrains.skia.Image.makeFromEncoded` 不能直接吃 BufferedImage，需经 `BufferedImage → PNG/byte[]` 或用 Skia `Image.makeFromBitmap`/`Bitmap.makeFromImage` 等 Skia API 转换；给出可工作的转换代码并注释）。
4. **多帧源切换 UI**：预览窗口顶部加帧源切换（`PNG 低频` / `H264 流(adb)` / `H264 流(App)`），切换时关闭旧帧源进程/连接、起新的。
5. **资源回收**：`screenrecord` 进程必须在窗口关闭/切换帧源时 `destroyForcibly()`，参考现有 `AdbScreenCapture.close()` 的模式。

**涉及文件**：`DevicePreviewWindow.kt`（新增 `AdbScreenRecordStream` + 帧源切换 UI + 解码）、`build.gradle.kts`（`jvmMain` 加 `javacv-platform` 依赖）。

### 3.4 需求 4 — PNG 流动态降频 + 降质（move 时）

**目标**：保留 PNG 方案；当用户正在预览窗口拖拽时，把拉帧间隔 `150ms → 15s`，并降低 PNG 质量（缩小尺寸、保持宽高比）。停止拖拽后恢复 `150ms` 与原始质量。

**实现要点**：
1. **「move 中」状态来源（推荐）**：复用现有 `pointerInput` + `TouchFilter` 的手势状态。在 `TouchFilter` 或预览 Composable 中维护一个 `isInteracting: Boolean`（`onDown`=true、`onUp`/`reset`=false），或用 Compose `mutableStateOf<Boolean>` 暴露给帧循环。
   - 不要新开一条独立的拖拽检测，避免与触摸透传逻辑状态不一致。
2. **降频**：把 `LaunchedEffect(capture)` 循环里的 `delay(FRAME_INTERVAL_MS)` 改为根据 `isInteracting` 动态选择：
   ```kotlin
   val interval = if (isInteracting) THROTTLE_INTERVAL_MS_MOVE else FRAME_INTERVAL_MS
   delay(interval)   // 150ms 常态 / 15s move 中
   ```
   `THROTTLE_INTERVAL_MS_MOVE = 15_000L`、`FRAME_INTERVAL_MS = 150L`。
3. **降质（保持宽高比，缩小尺寸）**：`screencap` 无质量参数，但支持把画面缩到指定尺寸。推荐：
   - `screencap -p` 拉全尺寸 → 用 Skia 在 PC 端缩放/降采样后展示（轻量、不改 adb 命令）；**或**
   - 用 `adb exec-out screencap -p` 后，PC 端对 bytes 先 decode 成 `Bitmap` → `makeScaled(w/2, h/2)`（宽高比不变，按比例缩）→ 再转 `ImageBitmap`。
   - move 中用缩放后的低质帧；常态用原始帧。两种帧缓存一份，切换时丢弃旧帧避免拼接错位。
   - ⚠️ 不要在 move 中丢弃正在显示的最后一帧，保持画面连续（只是更新变慢）。
4. **状态条文案**：move 中显示「PNG 节流中（15s/帧·降质）」，常态显示「PNG 低频预览」。
5. **交互体验**：抬手后应**立即补一帧**（不等 15s），即 `onUp` 触发一次 `captureFrame()`，再恢复 `150ms` 节奏。

**涉及文件**：`DevicePreviewWindow.kt`（帧循环动态 delay + 降质 + 交互状态联动 + 文案）。

### 3.5 需求 5 — 预览窗口置顶（always-on-top）

**目标**：预览窗口默认置于屏幕最顶，不被其他应用遮挡；并提供开关让用户可关闭置顶。

**实现要点**：
1. **Compose Desktop API**：`WindowState`（`androidx.compose.ui.window.WindowState`）提供 `alwaysOnTop: Boolean` 读写属性。当前 `DevicePreviewWindow` 已使用 `rememberWindowState(width = 420.dp, height = 760.dp)` 创建 windowState —— 将 `alwaysOnTop` 默认设为 `true`：
   ```kotlin
   val windowState = rememberWindowState(
       width = 420.dp,
       height = 760.dp,
       alwaysOnTop = true,   // 默认置顶
   )
   ```
2. **默认行为**：窗口创建时 `alwaysOnTop = true`。这是硬编码默认值，无需读取配置即可生效。
3. **动态切换**：窗口开启后用户可通过开关切换：
   ```kotlin
   var alwaysOnTop by remember { mutableStateOf(true) }
   LaunchedEffect(alwaysOnTop) {
       windowState.alwaysOnTop = alwaysOnTop
   }
   ```
   ⚠️ 注意：`rememberWindowState` 构造函数参数只在窗口首次 show 时生效；后续通过 `windowState.alwaysOnTop` setter 动态修改（Compose Desktop 1.6+ 支持运行时修改该属性）。若构造函数未提供 `alwaysOnTop` 参数，可只设置默认值后再通过 setter 修改。
4. **UI 开关位置**：放在预览窗口**顶部状态条**（即现有的 `Row` 内，左侧显示分辨率/旋转，右侧显示帧源信息）。在右侧 `BasicText` 旁新增一个**可点击的文字按钮**：
   - 置顶时显示 `📌 置顶`（或文字 `置顶`，用 `BasicText` + `Modifier.clickable` 即可，无需引入图标库）。
   - 取消时显示 `取消置顶`。
   - 点击时翻转 `alwaysOnTop` 状态并写回配置。
   - ⚠️ 当前窗口标题栏是系统原生，不支持自定义按钮，因此必须放在窗口内容区的状态条里。
5. **状态持久化**：将用户最后一次置顶开关选择写入配置（`DesktopAppSettingsStore`），下次打开预览窗口时恢复。具体做法：
   - `AppSettings`（`src/commonMain/kotlin/com/newchar/debug/pc/config/AppSettings.kt`）新增字段 `previewAlwaysOnTop: Boolean = true`。
   - 在 `DevicePreviewWindow` 顶层读取：`val settings = remember { DesktopAppSettingsStore().loadSync() }`，将 `settings.previewAlwaysOnTop` 作为 `alwaysOnTop` 的初始值。
   - 用户切换时写回配置：
     ```kotlin
     LaunchedEffect(alwaysOnTop) {
         withContext(Dispatchers.IO) {
             DesktopAppSettingsStore().save(
                 settings.copy(previewAlwaysOnTop = alwaysOnTop)
             )
         }
     }
     ```
   - ⚠️ `AppSettings` 新增字段要带默认值（`true`），以兼容旧配置文件。`DesktopAppSettingsStore` 的 `parse`/`buildContent` 是字符串级操作，不需要改动 —— 只影响 `copy(previewAlwaysOnTop = ...)` 写入时的额外一行 `previewAlwaysOnTop=true`。
6. **兼容性**：`WindowState.alwaysOnTop` 在 Compose Desktop 1.6+ 可用（本项目 `compose-gradle-plugin:1.9.2`，无兼容问题）。macOS 平台下 `alwaysOnTop` 等效于窗口层级 "always on top"；Windows 下等效于 `WS_EX_TOPMOST`；Linux 下等效于 `NET_WM_STATE_ABOVE`。
7. **注意事项**：
   - 不要在 `DevicePreviewWindow` 中频繁调用 setter（每次 UI 交互才触发一次，无需额外节流）。
   - 切换 `alwaysOnTop` 时不要重启窗口，只修改属性即可。

**涉及文件**：`DevicePreviewWindow.kt`（状态条增加置顶开关 + windowState 控制）、`AppSettings.kt`（新增 `previewAlwaysOnTop` 字段）。

---

## 四、涉及文件清单

| 文件 | 改动 | 所属需求 |
|---|---|---|
| `app_pc_debug_tools/src/commonMain/.../device/HeartbeatManager.kt` | 暴露 `lastContact(deviceId)` 或 `lastHeartbeatAt` 状态供 UI 读 | 需求 1 |
| `app_pc_debug_tools/src/jvmMain/.../device/preview/DevicePreviewWindow.kt` | 通信状态指示、H264 帧源 `AdbScreenRecordStream`、帧源切换 UI、PNG 动态降频降质、置顶开关 | 需求 1/3/4/5 |
| `app_pc_debug_tools/src/jvmMain/.../device/preview/AppStreamServer.kt` | **新建**：接 App 端 6667 推流并解码 | 需求 2 |
| `app_pc_debug_tools/build.gradle.kts` | `jvmMain` 加 `javacv-platform`（H264 解码） | 需求 2/3 |
| `lib_debug_touch/.../touch/ScreenRecordService.java` | 改 MediaCodec + 推流（方案 B）；保留 MP4 落盘 | 需求 2 |
| `lib_debug_touch/.../touch/ScreenRecordManager.java` | facade 增加 startStream/stopStream | 需求 2 |
| `lib_debug_touch/.../plugin/TouchRestorePlugin.java` | 新增「实时推流」开关 UI | 需求 2 |
| `lib_debug_touch/src/main/AndroidManifest.xml` | 视需要声明 INTERNET（推流到 reverse 端口） | 需求 2 |
| `app_pc_debug_tools/src/commonMain/.../config/AppSettings.kt` | 新增字段 `previewAlwaysOnTop: Boolean = true` | 需求 5 |

---

## 五、验收标准

- [ ] **需求 1**：预览窗口显示通信状态指示；App(PCToolsPlugin) 在线时为「已联通」，杀掉 App/断开 reverse 后在阈值内变「未联通」并给出排障提示。
- [ ] **需求 2**：设备端开启「实时推流」后，PC 端能收到 H264 帧并解码渲染；停止推流 socket 正常关闭；同时 MP4 录像不受影响。
- [ ] **需求 3**：预览窗口切到「H264 流(adb)」后，用 `adb exec-out screenrecord --output-format=h264 -` 拉流并解码显示，帧率/延迟显著优于 PNG 方案；窗口关闭/切换时 screenrecord 进程被回收。
- [ ] **需求 4**：常态 PNG `150ms/帧` 原始质量；move 时自动切到 `15s/帧` 且尺寸缩小（宽高比不变）；抬手后立即补一帧并恢复 `150ms` 原始质量。
- [ ] **需求 5**：预览窗口默认置顶（不被其他窗口遮挡）；点击状态条「置顶」按钮可关闭置顶（窗口不再置顶），再次点击恢复；关闭 App 后重新打开预览窗口，置顶状态与上次一致。
- [ ] 三种帧源（PNG / adb H264 / App H264）可在预览窗口切换且互不泄漏资源。
- [ ] 各帧源在设备离线/adb 不可用时优雅提示，不崩溃。
- [ ] 旋转后 H264 与 PNG 两种帧源的坐标映射（`PreviewCoordinateMapper`）均正确。

---

## 六、给 AI 的实现顺序建议

1. 需求 5（窗口置顶）：最小改动，直接在 `rememberWindowState` 加 `alwaysOnTop = true` 即可，5 分钟完成。
2. 需求 1（通信状态）：先做，最小改动，立即提升可观测性。
3. 需求 4（PNG 降频降质）：在现有 `DevicePreviewWindow` 上改，无新依赖，可快速验证 move 节流效果。
4. 需求 3（adb H264）：引入 JavaCV，先把「拉流→解码→渲染」单链路打通，再做帧源切换 UI。
5. 需求 2（App 推流）：最后做，涉及设备端 MediaCodec 改造与 PC 端接收，复杂度最高；可与需求 3 复用 PC 端解码器。
