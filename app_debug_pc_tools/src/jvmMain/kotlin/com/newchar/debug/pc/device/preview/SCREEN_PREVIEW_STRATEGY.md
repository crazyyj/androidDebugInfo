# 设备屏幕实时预览 —— 帧采集策略设计文档（终稿）

> **模块路径** `app_pc_debug_tools/src/jvmMain/kotlin/com/newchar/debug/pc/device/preview/`  
> **版本** 2026-07-25 v4 · 已消除疑问项 · 新增本地持久化缓存  
> **适用场景** PC 端通过 USB/WiFi 远程预览 Android 设备实时画面  

---

## 1. 背景与问题

### 1.1 现状

当前 PC 端预览窗口提供三种帧源，定义于 `DevicePreviewWindow.kt:65-69`：

| 帧源 | 枚举 | 获取间隔 | 延迟 | 180s 上限 |
|------|------|----------|------|-----------|
| PNG 截图 | `PNG` | 150ms/帧（交互时 15s/帧降质） | ~200-500ms | 无 |
| adb H264 流 | `ADB_H264` | 理论连续，实际 4-8fps | 1-3s | ✅ 存在 |
| App 推流 | `APP_H264` | 理论连续 | 取决于设备端编配 | 取决于设备端 |

### 1.2 核心问题

1. **adb screenrecord 是「录制工具」而非「实时推流协议」**——内部编码器会缓冲多帧后批量写入 stdout，这是 Android 源码层面的行为，无法通过参数消除
2. **180 秒硬上限**——`screenrecord` 源码强制限制最大录制时长，到期后进程退出，当前代码仅 `delay(300L)` 后重启，期间画面黑屏
3. **FFmpeg 初始探测开销**——每次重启后 `FFmpegFrameGrabber` 需重新分析流，即使设置 `analyzeduration=0` 仍有首次出帧延迟
4. **Java2DFrameConverter 三层转换**——FFmpeg Frame → Java2D BufferedImage → Compose ImageBitmap，增加每帧处理耗时

### 1.3 方案总览

```
┌──────────────────────────────────────┐
│             应用启动时                │
│  从本地存储加载所有已探测设备的配置    │
│  以设备序列 ID 为 key 持久化          │
│  (JSON 文件 / 配置数据库)              │
└──────────┬───────────────────────────┘
           │
           ▼
┌──────────────────────────────────────┐
│        设备连接时                     │
│  按序列 ID 匹配本地配置               │
│  匹配成功 → 直接使用缓存，不探测      │
│  匹配失败 / 配置失效 → 执行探测       │
│  探测结果以序列 ID 写入本地存储        │
└──────────┬───────────────────────────┘
           │
     ┌─────┴─────┐
     │  探测结果   │
     ├───────────┤
     │ 支持       │ 不支持
     ▼           ▼
  ┌────────┐ ┌────────┐
  │ 策略 A  │ │ 策略 B  │
  │ YUV    │ │ H264   │
  │ 原始帧  │ │ 管道流  │
  │ 带宽监控 │ │ 直接使用 │
  │ 降级触发 │ │        │
  │        │ │        │
  └───┬────┘ └───┬────┘
      │           │
      │ 用户打开预览窗口，直接读取缓存结果
      │ 不重复探测，使用正确策略启动
      │           │
      └─────┬─────┘
            │ 运行时失败
            ▼
       ┌────────┐
       │ 策略 C  │
       │ PNG 截图│
       └────────┘

共同点（不分策略）：
• --time-limit 180 自然退出，不主动 destroy
• 进程退出后立即重启，delay(50ms)
• 降级单向，不自动回退
```

**三个策略**：

| 策略 | 名称 | 适用条件 | 延迟 | 带宽 |
|------|------|---------|:----:|:----:|
| **A** | YUV 原始帧 | 设备支持 `--raw-frames` | ~50-100ms | 高（需监控降级） |
| **B** | H264 管道流 | 所有 Android 4.4+ 设备 | 1-3s | 低 |
| **C** | PNG 截图兜底 | 所有 Android 4.0+ 设备 | 200-500ms | 中 |

---

## 2. 策略 A：YUV 原始帧

### 2.1 适用条件

**探测时机**：设备连接（扫描发现 / USB 插入）时完成，非打开预览窗口时。

**三次缓存层级**：
1. **内存缓存**：设备连接时探测结果写入 `DeviceInfo` 或设备状态
2. **本地持久化**：以设备序列 ID 为 key 写入本地磁盘（JSON 文件），下次应用启动时优先加载
3. **预览时使用**：打开预览窗口时，先查内存缓存，未命中则查本地持久化，都未命中则重新探测

**启动加载流程**：
```
应用启动
    │
    ▼
读取本地存储的设备探测缓存文件
    │
    ▼
解析 JSON，建立 serialId → rawFramesSupported 映射
    │
    ▼
设备连接时，按 serialId 匹配缓存
    ├── 匹配成功 → 直接使用，不探测
    └── 匹配失败 → 执行探测，结果写入本地存储
```

**缓存失效判断**（满足任一即重新探测）：
- 本地缓存文件不存在
- 匹配的 serialId 不存在于缓存中
- 缓存时间戳超过 24 小时（`staleThreshold = 24h`）
- 用户手动触发「重新检测」

**探测方法**：

```bash
# 探测命令：启动 screenrecord --raw-frames，500ms 内判断
adb -s <deviceId> exec-out screenrecord --raw-frames --time-limit 180 -

# 判断标准（500ms 内完成）：
# - 进程存活 (isAlive) 且管道有数据 (available > 0) → 支持 YUV
# - 进程立即退出 (exitCode ≠ 0) → 不支持，走 H264
```

**关键细节**：探测不等待 `--time-limit` 到期，500ms 后直接 `destroy()` 进程。

```kotlin
suspend fun supportsRawFrames(executor: AdbCommandExecutor, deviceId: String): Boolean {
    return try {
        val process = ProcessBuilder(
            executor.resolveAdbCommand(
                "-s", deviceId, "exec-out", "screenrecord", "--raw-frames", "--time-limit", "180", "-"
            )
        ).start()
        delay(500)
        val result = process.isAlive && process.inputStream.available() > 0
        process.destroy()
        process.waitFor(1, TimeUnit.SECONDS)
        if (process.isAlive) process.destroyForcibly()
        result
    } catch (e: Exception) { false }
}
```

**为什么要运行时探测而不是查版本号？**
- 部分 Android 10+ 设备定制 ROM 可能移除了 `--raw-frames`
- 部分 Android 10 以下设备通过 OTA 可能支持
- 探测在设备连接时触发，不影响预览打开的体验

**缓存策略**：探测结果以设备序列 ID 为 key 写入本地持久化存储（JSON 文件），下次应用启动时优先加载。预览窗口打开时直接读取内存或本地缓存，不重复探测。

### 2.2 输出格式说明

**重要前提**：`--raw-frames` 是 Android 10 引入的实验性功能，不同厂商 ROM 的输出格式**可能不同**。首次部署时，必须在目标设备上 dump 实际输出以确认格式。

**已知有三种可能的输出格式**：

| 格式 | 结构 | 检测方式 | 常见厂商 |
|------|------|---------|---------|
| **无帧头** | 连续 NV12 帧，无分隔符，每帧固定 `w×h×1.5` bytes | 先查 `wm size` 获取分辨率，按固定大小切分 | AOSP 部分版本 |
| **文本帧头** | `"width height timestamp_ns\n"` + Y plane + UV plane | 首字节是 ASCII 数字字符 | 部分厂商定制 |
| **二进制帧头** | 12 bytes: width(u32) + height(u32) + timestamp(u32) + NV12 数据 | 首字节是非 ASCII 数字 | 部分厂商定制 |

**推荐方案**：先尝试「无帧头」模式（通过 `adb shell wm size` 获取分辨率），如果流解析失败再尝试「有帧头」模式。

```bash
# 正确的命令（不含 --output-format，raw-frames 固定输出 NV12）
adb -s <deviceId> exec-out screenrecord --raw-frames --time-limit 180 -
```

**鲁棒解析策略**：

```kotlin
fun createRawFrameParser(
    input: InputStream,
    width: Int,        // 通过 adb shell wm size 预先获取
    height: Int,
    onFrame: (ByteArray) -> Unit,  // NV12 帧数据回调
): Unit {
    val frameSize = width * height * 3 / 2
    val buffer = ByteArray(frameSize)
    
    // 尝试读取前 12 bytes 判断有无帧头
    val peek = ByteArray(12)
    input.mark(16)
    input.read(peek)
    input.reset()
    
    val hasHeader = !isNv12Data(peek, frameSize)  // 如果不像 NV12 数据，则认为有帧头
    
    if (hasHeader) {
        // 有帧头模式：解析帧头，然后读取 NV12 数据
        parseFramesWithHeader(input, width, height, onFrame)
    } else {
        // 无帧头模式：按固定大小切分
        parseFramesFixedSize(input, frameSize, onFrame)
    }
}

/** 无帧头模式：按固定大小连续读取 NV12 帧 */
fun parseFramesFixedSize(input: InputStream, frameSize: Int, onFrame: (ByteArray) -> Unit) {
    val buffer = ByteArray(frameSize)
    while (true) {
        readFully(input, buffer) ?: break
        onFrame(buffer)
    }
}

/** 判断前 12 bytes 是否不像 NV12 数据（NV12 的 Y 平面值在 0-255 范围，帧头会有 ASCII 数字或规范非零值） */
private fun isNv12Data(data: ByteArray, frameSize: Int): Boolean {
    // 无帧头时，前 12 bytes 就是 Y 平面的一部分
    // Y 值通常在 16-235 范围（视频范围）或 0-255（全范围）
    // 帧头（文本或二进制）通常有规律的模式
    // 这个启发式判断需要在实际设备上验证
    return true  // 默认假设无帧头，这是最安全的假设
}
```

**首次部署的必须步骤**：

```bash
# 步骤 1: 在目标设备上 dump 前 100KB
adb -s <deviceId> exec-out screenrecord --raw-frames --time-limit 5 - > /tmp/raw_frames_dump.bin

# 步骤 2: 用 hexdump 查看前 128 bytes，确认格式
hexdump -C /tmp/raw_frames_dump.bin | head -20

# 步骤 3: 根据实际格式，固定解析路径
# 这样后续运行时就不需要猜测了
```

### 2.3 带宽监控与降级触发

**背景**：YUV NV12 原始帧体积大，1080p 单帧约 3.1MB，20fps 下约 62MB/s，超出 USB 2.0 实际可用带宽（~40MB/s）。

**但注意**：帧数据已经由 screenrecord 推送到 adb 管道，PC 端「丢弃帧」**不能减少管道带宽消耗**。丢弃帧只减少 CPU 渲染开销。

**实际能做的事**：监控实际到达带宽，作为降级触发信号。

```
┌─────────────────────────────────────┐
│ 启动 YUV 流                           │
│ 初始目标渲染帧率: 20fps                │
└──────────────┬──────────────────────┘
               │
               ▼ (每秒)
┌─────────────────────────────────────┐
│ 测量本秒收到的字节数 bytesThisSec     │
│ 计算实际帧到达率: arrivalFps          │
│                                      │
│ 管道已经满载（bytesThisSec 接近极限）→ │
│ 渲染帧率 = min(渲染帧率, arrivalFps)  │
│ 降低渲染帧率可减少 CPU 开销            │
│                                      │
│ if arrivalFps < 5fps 持续 3 秒:       │
│   触发降级 → 切到 H264                │
└─────────────────────────────────────┘
```

**关键参数**：

| 参数 | 值 | 说明 |
|------|----|------|
| `DEGRADE_FPS_THRESHOLD` | 5 | 帧到达率低于此值持续 3 秒则降级 |
| `DEGRADE_DURATION_S` | 3 | 连续低于阈值的秒数 |
| `MAX_RENDER_FPS` | 20 | 最高渲染帧率 |
| `MIN_RENDER_FPS` | 5 | 最低渲染帧率，低于此值降低渲染开销无意义 |

**适配不同分辨率**：

| 分辨率 | NV12 单帧 | 20fps 带宽 | 预计实际帧到达率 (USB 2.0) | 建议 |
|--------|:---------:|:----------:|:------------------------:|:----:|
| 1080×1920 | 3.1MB | 62MB/s | ~12fps | 可接受，降级阈值 5fps |
| 720×1280 | 1.4MB | 28MB/s | ~20fps | 良好 |
| 540×960 | 0.8MB | 16MB/s | ~20fps | 良好 |

**WiFi 场景**：adb WiFi 实际带宽通常为 5-15MB/s，帧到达率会自然降到 5-8fps，低于阈值 3 秒后自动降级到 H264。

### 2.4 YUV→RGB 转换

**Skia 不支持直接接受 YUV 数据**。`Image.makeFromPixels()` 需要 BGRA/N32 格式像素数据，因此必须做 YUV→RGB 转换。

**实现路径**：

```
NV12 帧数据
    ↓
逐像素 YUV→RGB 转换 (BT.601 全范围)
    ↓
BGRA byte array (4 bytes/pixel)
    ↓
Image.makeFromPixels(info, bgraBytes, rowBytes)
    ↓
toComposeImageBitmap()
    ↓
Compose Image() 渲染
```

**性能**：纯 Java 循环，1080p 约 5-10ms/帧，现代桌面 CPU 上完全可接受。相比 H264 方案（FFmpeg 解码 3-5ms + 转换 2-3ms），总耗时接近，但**端到端延迟低得多**（因为省去了设备端编码缓冲的 1-2 秒）。

### 2.5 180 秒自然退出 + 立即重启

**方案**：使用 `--time-limit 180` 让进程自然退出，不需要主动 destroy。

```
时间轴：
0s                                         180s      180.05s
├────────────── round 1 ────────────────────┤├──50ms──┤
                                            ↑         ↑
                                      进程自然退出   立即重启
                                      (EOF 触发)   round 2
```

**优势**（相比「主动 destroy」方案）：
- 没有「提前计算时间」的误差
- 进程自然清理，不遗留僵尸进程
- 退出时最后一帧数据完整送达

**伪代码**：

```kotlin
val RESTART_DELAY_MS = 50L

while (!closed.get() && coroutineContext.isActive) {
    val process = startScreenRecord("--time-limit", "180", /* ... */)
    currentProcess.set(process)
    try {
        // 阻塞读取，直到进程退出（EOF）或异常
        collectFrames(process.inputStream, onFrame)
    } finally {
        currentProcess.compareAndSet(process, null)
        // 进程已自然退出，仅做安全清理
        runCatching { process.destroy() }
        runCatching { process.waitFor(1, TimeUnit.SECONDS) }
        if (process.isAlive) process.destroyForcibly()
    }
    // 仅等待进程资源回收
    if (!closed.get()) delay(RESTART_DELAY_MS)
}
```

---

## 3. 策略 B：H264 管道流

### 3.1 原理

```
adb exec-out screenrecord --time-limit 180 --output-format=h264 -
```

H264 Annex-B 裸流直接写入 stdout，PC 端通过 `process.inputStream` 实时读取，FFmpeg 逐帧解码。**这是边录边解码的实时流，不是先录完再传的文件。**

### 3.2 适用范围

- 所有 Android 4.4+ 设备
- `--raw-frames` 探测失败的设备
- 降级链的第二环

### 3.3 180 秒自然退出 + 立即重启

与策略 A 相同，使用 `--time-limit 180` 自然退出。**衔接开销比 YUV 大**，但这是 H264 的本质限制：

| 环节 | 耗时 |
|------|:----:|
| 进程自然退出 + 50ms delay | ~50ms |
| 新进程启动 | ~200ms |
| FFmpeg 首帧出帧（SPS/PPS + 第一个 I 帧） | ~300-500ms |
| **合计中断时间** | **~550-750ms** |

### 3.4 FFmpeg 参数优化

| 参数 | 当前值 | 优化值 | 说明 |
|------|:------:|:------:|------|
| `probesize` | 32768 | 16384 | 减半，加速流探测 |
| `analyzeduration` | 0 | 500000 | 0.5s 足够解析 SPS/PPS，比 0 更稳定 |
| 帧转换 | Java2DFrameConverter | 直接 AVFrame → BufferedImage | 跳过一层中间转换 |

---

## 4. 策略 C：PNG 截图兜底

### 4.1 原理

```bash
adb exec-out screencap -p
```

每次调用 adb 拉取一张 PNG 截图。已有实现 `AdbScreenCapture`（`DevicePreviewWindow.kt:241-289`）。

### 4.2 适用范围

- 策略 A / B 均失败时的最终兜底
- 兼容所有 Android 4.0+ 设备，无任何依赖
- 延迟约 200-500ms，交互时降质到 15s/帧以减少带宽

---

## 5. 降级链与切换逻辑

### 5.1 完整流程

**阶段零：应用启动时（触发一次）**

```
应用启动
    │
    ▼
┌──────────────────────────────────────────────────────────┐
│ 从本地磁盘加载设备探测缓存文件                            │
│ 文件路径: ${appDataDir}/device_preview_cache.json        │
│                                                          │
│ 格式:                                                    │
│ {                                                        │
│   "devices": {                                           │
│     "serialId_1": {                                      │
│       "rawFramesSupported": true,                        │
│       "probeTimestamp": 1690000000000,                   │
│       "adbVersion": "1.0.41"                             │
│     },                                                   │
│     "serialId_2": { ... }                                │
│   },                                                     │
│   "formatVersion": 1                                     │
│ }                                                        │
│                                                          │
│ 解析结果: 建立 serialId → probeResult 内存映射            │
│ 未找到文件或格式错误 → 视为空缓存，不报错                  │
└──────────────────────────────────────────────────────────┘
```

**阶段一：设备连接时（触发一次）**

```
设备被扫描发现 / USB 插入
    │
    ▼
获取设备序列 ID
    │
    ▼
┌─────────────────────────────────────────────────┐
│ 按序列 ID 匹配本地缓存                             │
│                                                  │
│ 匹配成功 → 检查缓存是否有效                        │
│   ├── 有效（probeTimestamp 在 24h 内）            │
│   │   → 直接使用缓存，不探测，跳过阶段一           │
│   │   DeviceInfo.rawFramesSupported = cached      │
│   │                                              │
│   └── 失效（超过 24h）                            │
│       → 重新探测，更新缓存                         │
│                                                  │
│ 匹配失败（新设备/缓存被清除）                       │
│   → 执行探测                                      │
│                                                  │
│ 探测 screenrecord --raw-frames 是否支持           │
│ 500ms 短测试，不等 --time-limit 到期              │
│                                                  │
│ 探测结果写入本地持久化存储                          │
│   serialId → probeResult, timestamp               │
│ 同时更新内存缓存                                   │
└──────────────────────────────────────────────────┘
```

**缓存记录格式（本地 JSON 文件）**：

```json
{
  "formatVersion": 1,
  "devices": {
    "ABCDEF123456": {
      "rawFramesSupported": true,
      "probeTimestamp": 1690000000000,
      "adbVersion": "1.0.41"
    }
  }
}
```

**缓存文件路径**：`${appDataDir}/device_preview_cache.json`（使用 `DesktopAppSettingsStore` 相同的应用数据目录）

**阶段二：用户打开预览窗口时（直接使用缓存）**

```
用户打开预览窗口
    │
    ▼
读取设备缓存的探测结果
    │
    ├── rawFramesSupported == true  → 启动策略 A (YUV)
    │                                  │
    │                                  ▼
    │                            显示 YUV 画面
    │                            带宽监控后台运行
    │
    └── rawFramesSupported == false → 启动策略 B (H264)
                                       │
                                       ▼
                                 显示 H264 画面
```

**缓存刷新时机**：
- 设备断开后再连接时：检查缓存是否在 24h 内，是则直接使用，否则重新探测
- 用户手动触发「重新检测」时：强制重新探测，更新本地存储
- 同一设备连续连接有效期内不重复探测

### 5.2 运行时降级

```
                  ┌──────────┐
                  │ 策略 A/B │
                  │ 运行中    │
                  └────┬─────┘
                       │
         ┌─────────────┼─────────────┐
         ▼             ▼             ▼
   进程异常退出   连续 3 帧失败   带宽持续 < 5fps
    (exitCode≠0)  (解码异常)    (仅策略 A，持续 3s)
         │             │             │
         └─────────────┼─────────────┘
                       ▼
                 ┌──────────┐
                 │ 策略 C   │
                 │ PNG 截图  │
                 └──────────┘
```

### 5.3 不回退原则

**降级是单向的**，一旦降到策略 C 不会自动回退到 A/B，避免振荡。用户可：
- 手动点击帧源标签尝试恢复
- 关闭窗口重新打开（使用内存/本地缓存结果，不重新探测）

**缓存重置**：设备断开后重新连接，若缓存有效期内（24h）则直接使用；超期或手动触发「重新检测」时重新探测并更新本地存储。**应用重启不丢失缓存**，下次启动自动加载本地持久化文件。

---

## 6. 边界问题处理

### 6.1 进程泄漏防护

1. `currentProcess` 使用 `AtomicReference` 保证可见性
2. `close()` 方法同时设置 `closed=true` 和 `destroy()` 进程
3. `DisposableEffect` 在 Compose 离开组合时调用 `close()`
4. 协程取消时检查 `coroutineContext.isActive` 避免启动新进程
5. `destroyForcibly()` 作为最终保障，防止 `destroy()` 无效的僵尸进程

### 6.2 帧率与背压

| 策略 | 设备端帧率 | PC 端消费 | 背压机制 |
|------|-----------|----------|----------|
| **A: YUV** | 取决于 screenrecord（~20fps） | 同步解析+渲染 | 管道自然阻塞（pipe buffer 满 → screenrecord write 阻塞 → 帧率下降） |
| **B: H264** | screenrecord 默认 ~20fps | FFmpeg grabImage 阻塞 | 自然背压：grabImage 阻塞直到下一帧就绪 |
| **C: PNG** | 按需触发 | 150ms 轮询 | 固定间隔，天然限流 |

**YUV 模式背压需要特别注意**：由于管道没有编解码器缓冲，如果 PC 端消费慢，adb 管道缓冲区会迅速填满，导致 screenrecord 进程阻塞。**带宽监控 + 降级触发**正是为了解决这个问题。

### 6.3 设备旋转处理

- `DevicePreviewWindow` 已有 `queryDisplayInfo()` 每 500ms 轮询 `wm size` 和 `user_rotation`
- 录制期间旋转，screenrecord 会自动跟随画面方向，分辨率不变
- **YUV 帧头每帧携带 width/height**，天然支持动态分辨率变化
- H264 流中分辨率变化可能导致 FFmpeg 需要重新探测，但概率极低（用户不会在录制时频繁旋转）

### 6.4 多设备并发

- 每个 `DevicePreviewWindow` 持有独立的流实例，互不干扰
- 限制同时预览的设备数量（建议最多 3 个），特别是 YUV 模式
- 3 个设备同时 YUV 1080p@5fps 约 46MB/s，仍在 USB 2.0 极限内

### 6.5 错误恢复

| 错误场景 | 处理方式 | 用户感知 |
|---------|---------|---------|
| USB 拔出 | 进程 IOException → 降级策略 C → 提示「设备离线」 | 状态栏红色提示 |
| WiFi 断开 | 同上 | 同上 |
| screenrecord 被 kill | 流中断 → 自动重启 → 若仍失败则降级 | 可能闪一帧黑屏 |
| 设备端屏幕关闭 | screenrecord 继续输出黑帧，不影响流程 | 看到黑屏（符合预期） |
| PC 端内存不足 | OOM → catch → 降级到 PNG | 切换提示 |
| YUV 带宽持续满载 | 帧率自动降到 5fps | 无感知，可查看帧率显示 |

---

## 7. 实现清单

### 7.1 策略 A 新增（YUV 原始帧）

- [ ] 新增 `AdbRawFrameStream.kt`
  - 进程管理：`screenrecord --raw-frames --time-limit 180`
  - 180 秒自然退出 + 立即重启循环
  - 鲁棒帧头解析（兼容文本/二进制两种格式）
  - 带宽监控与降级触发
- [ ] 新增 `YuvFrameConverter.kt`
  - NV12 → RGB 转换（BT.601 全范围）
  - BGRA byte array → Skia ImageBitmap
- [ ] 新增 `BandwidthMonitor.kt`
  - 每秒统计字节数
  - 动态调整目标帧率
  - 帧丢弃逻辑

### 7.2 策略 B 改造（H264 管道流）

- [ ] `AdbScreenRecordStream.kt`：改为 `--time-limit 180` 自然退出，移除主动 destroy 逻辑
- [ ] `AdbScreenRecordStream.kt`：重启间隔改为 `RESTART_DELAY_MS = 50L`
- [ ] `H264FrameDecoder.kt`：`probesize` → 16384，`analyzeduration` → 500000
- [ ] `H264FrameDecoder.kt`：优化 FFmpeg Frame → BufferedImage 转换路径

### 7.3 运行时探测与降级串联

- [ ] 新增 `runtimeProbeRawFrames()` 探测函数，在设备连接时调用
- [ ] 在 `DeviceInfo` 或设备状态中增加 `rawFramesSupported` 缓存字段
- [ ] 设备扫描发现 / USB 插入时触发探测，结果写入缓存
- [ ] **新增本地持久化存储**：`DeviceProbeCache` 类，以 JSON 文件存储设备序列 ID → 探测结果映射
- [ ] **应用启动时**：加载本地持久化缓存文件，建立内存映射
- [ ] **设备连接时**：按 serialId 匹配缓存，有效期内直接使用，超期重新探测
- [ ] **探测结果写入**：`serialId → { rawFramesSupported, probeTimestamp, adbVersion }` 写入本地 JSON 文件
- [ ] **缓存失效判断**：24h 时间戳阈值，或用户手动触发「重新检测」
- [ ] `DevicePreviewWindow.kt`：打开预览时读取缓存，选择策略 A 或 B
- [ ] `DevicePreviewWindow.kt`：运行时失败统一降级到策略 C（PNG）
- [ ] 降级状态可视化：当前使用哪个策略、帧率、带宽

---

## 8. 文件清单

| 文件 | 状态 | 职责 |
|------|:----:|------|
| `AdbRawFrameStream.kt` | 🆕 新增 | YUV 原始帧进程管理 + 180s 重启 + 带宽监控 |
| `YuvFrameConverter.kt` | 🆕 新增 | NV12 → RGB → Skia ImageBitmap |
| `BandwidthMonitor.kt` | 🆕 新增 | 吞吐测量 + 动态帧率调整 |
| `DeviceProbeCache.kt` | 🆕 新增 | 探测结果本地持久化（JSON 文件，按 serialId 索引） |
| `AdbScreenRecordStream.kt` | 🔧 改造 | 改为 180s 自然退出 + 50ms 重启 |
| `H264FrameDecoder.kt` | 🔧 改造 | FFmpeg 参数优化 + 转换路径优化 |
| `DevicePreviewWindow.kt` | 🔧 改造 | 运行时探测 + 降级串联 + UI 状态显示 |

---

## 附录 A：screenrecord 命令参考

```bash
# 探测 YUV 支持（500ms 内完成，不等进程退出）
adb -s <deviceId> exec-out screenrecord --raw-frames --time-limit 180 -

# 策略 A：YUV 原始帧
adb -s <deviceId> exec-out screenrecord --raw-frames --time-limit 180 -

# 策略 B：H264 管道流
adb -s <deviceId> exec-out screenrecord --time-limit 180 --output-format=h264 -

# 策略 C：PNG 截图（兜底）
adb -s <deviceId> exec-out screencap -p
```

## 附录 B：带宽监控伪代码

```kotlin
/**
 * 带宽监控器。
 *
 * 注意：此监控器不能控制管道带宽（字节已到达缓冲区），
 * 仅用于监控实际帧到达率，在带宽不足时触发降级信号。
 */
class BandwidthMonitor(
    private val onDegradeRequest: () -> Unit,  // 触发降级到 H264
) {
    private var bytesThisSecond = 0L
    private var frameCountThisSecond = 0
    private var arrivalFps = 0
    private var secondsBelowThreshold = 0

    fun onFrameReceived(frameSize: Int) {
        bytesThisSecond += frameSize
        frameCountThisSecond++
    }

    /** 每秒调用一次，由外部定时器触发 */
    fun onSecondTick() {
        val bytes = bytesThisSecond
        arrivalFps = frameCountThisSecond
        bytesThisSecond = 0L
        frameCountThisSecond = 0

        when {
            // 帧到达率低于阈值 → 累计秒数
            arrivalFps < DEGRADE_FPS_THRESHOLD -> {
                secondsBelowThreshold++
            }
            // 帧到达率恢复正常 → 重置计数器
            else -> {
                secondsBelowThreshold = 0
            }
        }

        // 连续 3 秒低于 5fps → 降级到 H264
        if (secondsBelowThreshold >= DEGRADE_DURATION_S) {
            onDegradeRequest()
            secondsBelowThreshold = 0
        }
    }

    companion object {
        const val DEGRADE_FPS_THRESHOLD = 5
        const val DEGRADE_DURATION_S = 3
    }
}
```

        onFpsChanged(targetFps)

        // 连续 3 秒低于 5fps，触发降级信号
        if (secondsBelowThreshold >= 3) {
            onDegrade()
        }
    }

    companion object {
        const val BANDWIDTH_HIGH = 40_000_000L  // 40 MB/s
        const val BANDWIDTH_LOW = 15_000_000L   // 15 MB/s
        const val MIN_FPS = 5
        const val MAX_FPS = 20
        const val ADJUST_STEP = 2
    }
}
```

## 附录 C：NV12→RGB 转换效率

1080×1920 分辨率下，纯 Java 逐像素转换：

```
YUV→RGB 运算量: 1920 × 1080 × 1.5 = 3.1M 像素
每像素: 3 次乘法 + 3 次加法 + 饱和裁剪
总运算: ~30M 整数运算
现代 CPU (M1/i7): < 5ms
```

**如果担心 Java 循环性能**，备选方案：
- 使用 `java.lang.Runtime` 调用 `libyuv` 原生库（通过 JNI）
- 使用 `java.awt.image` 的 `ColorConvertOp`（但需要 YUV → RGB 转换矩阵支持）
- 当前纯 Java 方案已足够，无需额外依赖

---

## 附录 D：疑问项消除记录

| 原疑问 | 解决方案 |
|--------|---------|
| Q1: `--raw-frames` 格式未知 | 三种格式兼容（无帧头/文本帧头/二进制帧头），首次部署 dump 确认后固定 |
| Q2: YUV 带宽超 USB 2.0 极限 | 不试图控制带宽（字节已到管道），改为监控帧到达率，低于 5fps 持续 3s 则降级到 H264 |
| Q3: YUV 能否替换更小格式 | NV12 是原始帧最小格式，YUV 与 H264 互补而非替代 |
| Q4: 170 主动 destroy vs 自然退出 | 改为 `--time-limit 180` 自然退出，不主动 destroy |
| Q5: 版本判断 | 改为运行时探测（500ms），不依赖 SDK 版本号 |
| Q6: 帧率限制 | 改为监控模式：不控制带宽，只监控帧到达率，触发降级信号 |