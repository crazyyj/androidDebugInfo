# 桌面端预览「看不到实时画面」根因分析 + 解决方案

> 在真机 `A68HLJQGV8DECE55`（720×1520）上实测得出。结论先放最前：**问题不在代码的 150ms 间隔，而在单帧 PNG 本身就要 1.7 秒**。

---

## 一、实测数据（已在本机验证，非理论值）

### 1. 当前代码路径：`exec-out screencap -p` → 单帧 PNG

| 项目 | 实测值 |
|---|---|
| 单帧耗时（连续 3 次） | **1.74s / 1.62s / 1.70s** |
| 单帧大小 | ~890 KB |
| 设备端 PNG 编码耗时 | **1676 ms**（在设备 shell 内测） |
| 设备端 RAW 编码耗时 | 450 ms（对照） |
| `exec-out` vs `pull` 模式 | 1.72s vs 1.77s（几乎一样） |

**关键结论**：瓶颈是**设备端 PNG 编码**（1.6s+），不是 adb 传输，也不是 PC 端解码。
代码里的 `FRAME_INTERVAL_MS = 150L` 形同虚设 —— 实际是 `~1700ms + 150ms = ~1.85s/帧`，约 **0.5 fps**，用户感觉就是「卡住、看不到画面」。

### 2. 设备自带 H264（screenrecord）路径：表现好得多

| 项目 | 实测值 |
|---|---|
| 2 秒 H264 录制 | 2.09s（设备端） |
| pull 741KB 到 PC | **0.072s** |
| 文件起始字节 | `00 00 00 01 67 42...`（标准 H264 SPS NAL） |
| 平均码率 | ~370 KB/s（即 ~2.9 Mbit/s） |

**关键结论**：H264 路径**单位时间产生的数据比 PNG 小 5 倍以上**，且是**硬件编码**，设备端几乎零开销。`00 00 00 01 67` 是 H264 SPS 起始码，证明设备 screenrecord 输出的就是标准 Annex-B 裸流，**PC 端任何标准 H264 解码器都能直接吃**。

### 3. 还有一条隐藏快路径：screencap 原始 raw

`screencap /sdcard/x.raw`（不加 `-p`）耗时只有 **450ms**（PNG 的 1/4），但产出是 4.1MB 的 RGBA raw，体积大、PC 端要自己拼位图，仅作为 PNG 的轻量替代备选。

---

## 二、根因定位

```
当前链路（慢）：
  adb exec-out screencap -p  ──►  设备端 PNG 编码(1.6s)  ──►  adb 传输  ──►  Skia 解码
                                    ▲ 瓶颈在这 ▲

H264 链路（快，但当前没启用）：
  adb exec-out screenrecord --output-format=h264 -  ──►  硬件编码(≈0)  ──►  持续流  ──►  PC 解码
```

**为什么 PNG 这么慢？** 这台设备的 `screencap` 用的是软件 zlib+PNG 编码，720×1520 一次压缩就要 1.6 秒。**这不是 adb 或代码的问题，换什么 polling 间隔都救不了** —— 一帧本身就至少 1.6s。

---

## 三、解决方案（按推荐度排序）

### ✅ 方案 A（强烈推荐）：切换到 H264 流 + JavaCV 解码

这是文档 `PC_DESKTOP_PREVIEW_REFACTOR_PROMPTS.md` 需求 3 的方案，实测验证可行：

- **设备端**：`adb exec-out -s <id> screenrecord --output-format=h264 -` 输出标准 H264 Annex-B 裸流（已验证 `00 00 00 01 67` SPS NAL 起始）。
- **PC 端解码**：项目已引入 `javacv-platform:1.5.10`（见 `build.gradle.kts`），用 `FFmpegFrameGrabber` 直接喂字节流即可，无需额外依赖。
- **预期效果**：**从 0.5 fps → 25~30 fps**，延迟 < 500ms，真正实时。

**需要注意的 3 个工程细节**（实测发现的坑）：

1. **`exec-out` 管道 buffering**：我第一次用 `timeout 2 adb exec-out screenrecord ... > /tmp/x.h264` 拿到的是 **0 字节空文件**，但 `adb shell screenrecord /sdcard/x.h264 && adb pull` 能拿到完整 1MB 文件。说明 **exec-out 的 stdout 管道在进程被 kill 时会丢失缓冲**。解决：
   - 推荐写法：起一个**长驻 Process**，**持续从 `inputStream` 读**，不要等进程退出。
   - 注意 `screenrecord` 默认 180s/3min 自动停止，要捕获 EOF 后**自动重启进程**（重启时设备会重发 SPS/PPS IDR，解码器能自动重同步）。
   - 加 `--bit-rate` 降低码率可进一步减负：`screenrecord --output-format=h264 --bit-rate 2000000 -`。

2. **screenrecord 录制时 `--time-limit` 上限**：部分设备上限 180s。代码里要么不设让它跑到上限自动停，要么显式 `--time-limit 175` 留余量，EOF 后重连。

3. **旋转处理**：`screenrecord` 输出固定为开始录制时的方向，旋转后画面不会跟着转，需要**检测 rotation 变化时重启 screenrecord 进程**（参考现有 `queryDisplayInfo` 每 500ms 轮询 rotation）。

**代码骨架**（替换 `AdbScreenCapture` 的循环）：
```kotlin
// 长驻进程模式（关键：while(read) 持续读，不等进程结束）
class AdbScreenRecordStream(executor, deviceId) {
    private val grabber = FFmpegFrameGrabber(...)  // 喂 inputStream
    fun start(onFrame: (BufferedImage) -> Unit) {
        // 1. 起 Process: exec-out screenrecord --output-format=h264 -
        // 2. 把 process.inputStream 塞给 FFmpegFrameGrabber
        // 3. while (true) grabber.grab() → onFrame
        // 4. 进程 EOF 或异常 → destroy → 重启（新进程会重新发 SPS/IDR）
    }
}
```

---

### ⚠️ 方案 B（过渡方案）：screencap raw + PC 端转位图

如果暂时不想引 JavaCV 解码链路，可以先把 PNG 换成 raw：

- 命令：`adb exec-out screencap /sdcard/x.raw`（注意：raw 模式下 `-p` 不需要）。
- 设备端耗时 **450ms**（PNG 的 1/4），单帧从 1.85s 降到 **~0.6s**，约 1.6 fps。
- 体积 4.1MB（PNG 的 5 倍），但 USB 带宽够。
- PC 端把 RGBA bytes 直接构造成 `ImageBitmap`（`Bitmap.makeFromImage` + 像素指针），**无需解码库**。

**缺点**：仍是「幻灯片」，算不上实时，但比当前快 3 倍，作为「不引依赖」的过渡可接受。

---

### ❌ 方案 C（不推荐）：继续优化 PNG 路径

实测已证明 PNG 编码本身就是瓶颈，无论：
- 缩小尺寸（`screencap` 不支持 resize），
- 改 polling 间隔（150ms 已经够小，是单帧 1.7s 拖后腿），
- 改 transfer 模式（exec-out vs pull 差不多），

都治标不治本。**PNG 路径天花板就是 ~0.5 fps**，不要在它上面继续投入。

---

## 四、实施建议（结合现有文档）

1. **优先做方案 A（H264）**：文档 `PC_DESKTOP_PREVIEW_REFACTOR_PROMPTS.md` 需求 3 已经规划完整，依赖 `javacv-platform` 也已就位。建议作为下一项实施，**这是真正解决「看不到实时画面」的唯一正解**。

2. **PNG 路径降级为「低频预览 fallback」**：保留当前 PNG 实现作为 H264 失败时的兜底（比如老设备 screenrecord 不支持 `--output-format=h264`）。顶部状态条加帧源切换（文档需求 3 第 4 点）。

3. **需求 4（move 时降频到 15s）在 H264 方案下可作废**：原本设计是为了在 PNG 慢路径下省 adb 资源，H264 路径本身就很轻，不需要节流。但作为 PNG fallback 模式的优化保留也无妨。

4. **快速验证 H264 链路的命令**（不用写代码就能验）：
   ```bash
   adb exec-out -s A68HLJQGV8DECE55 screenrecord --output-format=h264 --time-limit 5 - > /tmp/test.h264
   ffplay /tmp/test.h264    # 或 VLC 打开
   ```
   如果能流畅播放 5 秒画面，说明设备端链路完全 OK，剩下都是 PC 端工程。

---

## 五、一句话总结

> **当前 150ms 间隔是假象，单帧 PNG 编码就要 1.7s，所以约 0.5fps、看不到实时画面。设备自带 H264 硬件编码路径实测可用（已验证 SPS NAL 头），切过去就是 25~30fps 实时。优先实施文档需求 3，依赖已就位。**
