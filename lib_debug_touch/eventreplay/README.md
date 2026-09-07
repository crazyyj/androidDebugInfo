# 事件复现（融合 accesshelper + touch，位于 lib_debug_touch/eventreplay）

> 本功能已并入 **`lib_debug_touch`** 模块，源码位于
> `lib_debug_touch/src/main/java/com/newchar/debug/touch/eventreplay/` 目录（包名 `com.newchar.debug.touch.eventreplay`）。
> 不再作为独立 Gradle 模块存在。

把 [WoolHelper](https://github.com/crazyyj/WoolHelper) 中 **accesshelper** 模块（基于无障碍服务的语义动作流复现）
与本项目 **touch** 模块（基于 `Window.Callback` 的原始触摸采集）融合，
形成 **事件复现的又一种形式**。

核心目标：**把录制下来的 event JSON 化，使其可以被新代码直接读取、解析与处理。**

---

## 两种事件复现形式

| 形式 | 来源 | 数据 | 复现方式 |
| --- | --- | --- | --- |
| **形式一：touch（原始触摸）** | 原 `lib_debug_touch` | `RecordedEventSequence`（逐帧 MotionEvent 坐标 + 压力） | `MotionEventReplayer` 还原 `MotionEvent` 后派发 |
| **形式二：accesshelper（语义动作流）** | 移植自 WoolHelper `accesshelper` | `ActionStream`（点击/手势等语义动作） | `TouchToActionConverter` 转换 → `GesturePerformer` 经无障碍服务执行 |

同一个录制，先以 **形式一** 保存为 JSON，再经 `TouchToActionConverter` 转换为 **形式二** 的 JSON。
两种 JSON 均能被新代码 `RecordedEventSequence` / `ActionStream` 的 `fromJsonString` 解析处理。

---

## 模块结构

```
lib_debug_touch/src/main/java/com/newchar/debug/touch/eventreplay/
├── model/                  # 纯 Java、零依赖、可在普通 JVM 上单测
│   ├── Json.java           # 极简 JSON 编解码（不依赖 org.json）
│   ├── TouchPoint.java     # 单指针快照
│   ├── RecordedEvent.java  # 一条触摸事件（对应一个 MotionEvent）
│   └── RecordedEventSequence.java  # 一段事件序列（JSON 化载体）
├── replay/                 # 移植自 WoolHelper accesshelper 的“另一种复现”
│   ├── Action*.java        # Action / ActionLocal / ActionOutput / ActionStream
│   ├── TouchToActionConverter.java  # 触摸序列 -> 动作流（另一种形式）
│   ├── GesturePathAdapter.java / DefaultGesturePathAdapter.java
│   ├── GesturePerformer.java        # 经无障碍服务下发手势
│   └── AccessibilityActionReplayer.java
├── record/                 # 融合自 touch 模块的采集 / 回放
│   ├── TouchEventRecorder.java      # Window.Callback 代理采集触摸
│   └── MotionEventReplayer.java     # 还原 MotionEvent 回放
└── EventReplayManager.java          # 统一入口
```

---

## 使用方式

```java
EventReplayManager mgr = new EventReplayManager();

// 1) 录制：开始 -> 用户操作 -> 停止 -> 保存为 JSON
mgr.startRecording();
// ... 用户操作 ...
mgr.stopRecording();
mgr.saveRecordedEvent(new File(context.getExternalCacheDir(), "event.json"));

// 2) 新代码读取并处理（事件 JSON 化，可被解析）
RecordedEventSequence seq = EventReplayManager.loadEventSequence(eventFile);

// 3a) 形式一：以原始触摸事件复现
mgr.replayAsTouch(seq, targetView);

// 3b) 形式二（另一种形式）：转为语义动作流，经无障碍服务复现
mgr.replayAsAccessibility(seq, accessibilityService);

// 也可以直接处理外部给定的动作流 JSON（accesshelper 形式）
ActionStream stream = EventReplayManager.loadActionStream(externalJson);
```

---

## 验证（纯 JVM，无需 Android）

`tools/JvmRoundTrip.java` 在普通 JVM 上验证了“JSON 化 + 新代码可处理”：

```bash
mkdir -p /tmp/jvmout
javac -d /tmp/jvmout \
  lib_debug_touch/src/main/java/com/newchar/debug/touch/eventreplay/model/*.java \
  lib_debug_touch/src/main/java/com/newchar/debug/touch/eventreplay/replay/Action*.java \
  lib_debug_touch/src/main/java/com/newchar/debug/touch/eventreplay/replay/TouchToActionConverter.java \
  lib_debug_touch/tools/JvmRoundTrip.java
java -cp /tmp/jvmout com.newchar.debug.touch.eventreplay.JvmRoundTrip
```

它会生成两个示例 JSON（位于 `lib_debug_touch/eventreplay/sample/`）：
- `recorded_event_sample.json` —— 形式一的事件序列
- `action_stream_sample.json` —— 形式二的动作流（由前者转换而来）

两者均能被对应 `fromJsonString` 重新解析，证明 **“保存下来的 event 已经 JSON 化、可由新代码处理”**。
