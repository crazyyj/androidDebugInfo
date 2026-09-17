# 输入脚本 LQITS v1

PC 工具的设备详情页提供“输入脚本”入口。执行顺序如下：

1. 推送 `input_tool.dex`，以 shell UID 运行 `InputToolMain`。
2. 使用 `InputManager.injectInputEvent` 执行单指、多指和按键。
3. dex 无法启动时，PC 端使用 `adb shell input` 兜底。
4. 执行失败时在 PC 配置目录的 `input_failures` 保存屏幕截图。

本能力不注册 `AccessibilityService`。

## 基础结构

```json
{
  "magic": "LQITS",
  "version": 1,
  "scriptId": "login_case",
  "name": "登录流程",
  "sourceWidth": 1080,
  "sourceHeight": 2400,
  "targetPackage": "com.example.app",
  "steps": []
}
```

坐标模式：

- `source_pixel`：按录制分辨率等比例映射到目标屏幕，touch 模块默认使用。
- `normalized`：`x/y` 使用 `0..1` 比例坐标。
- `pixel`：直接使用目标设备像素坐标。

每一步均可配置：

- `delayBeforeMs`：执行前等待。
- `retryCount`：失败重试次数。
- `failurePolicy`：兼容字段，当前版本出现异常后仍会继续尝试后续步骤。

## 步骤类型

```json
[
  { "id": "tap", "type": "tap", "x": 320, "y": 640 },
  { "id": "hold", "type": "long_press", "x": 320, "y": 640, "durationMs": 800 },
  {
    "id": "swipe",
    "type": "gesture",
    "durationMs": 300,
    "points": [
      { "id": 0, "x": 500, "y": 1800, "timeMs": 0 },
      { "id": 0, "x": 500, "y": 400, "timeMs": 300 }
    ]
  },
  { "id": "back", "type": "key", "keyCode": 4 },
  { "id": "wait", "type": "wait", "waitMs": 1000 },
  { "id": "launch", "type": "launch_app", "packageName": "com.example.app" },
  { "id": "check", "type": "package_check", "expectedPackage": "com.example.app", "timeoutMs": 3000 },
  {
    "id": "node",
    "type": "node_tap",
    "selector": { "resourceId": "com.example.app:id/submit", "text": "提交" }
  }
]
```

`node_tap` 通过 `uiautomator dump` 定位第一个匹配节点。`resourceId`、`text`、`contentDescription`、`className` 中的非空字段同时匹配。

`multi_touch` 使用 touch 模块自动导出的 `frames`，每帧保留完整 `action`、相对时间和全部 pointer。原生多指注入失败时，会把每个 pointer 的轨迹依次降级为 `input swipe`；该兜底只能保证每条轨迹都被尝试，不能保持多指同步语义。

## touch 导出

```java
EventReplayManager manager = new EventReplayManager();
manager.startRecording();
// 执行待记录操作
manager.stopRecording();
manager.saveInputScript(outputFile);
```

`TouchRestorePlugin` 的录制按钮关闭时，也会自动保存到应用外部缓存目录的 `.v/scripts`。