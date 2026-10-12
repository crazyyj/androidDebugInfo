# 输入脚本 LQITS v1/v2

PC 工具的设备详情页提供“输入脚本”入口。每条指令按以下优先级执行，当前通道失败或不支持时自动尝试下一通道：

1. 推送 `input_tool.dex`，以 shell UID 运行 `InputToolMain`，优先使用 app_process 能力。
2. 安装并调用独立 UiAutomation instrumentation APK；该执行器面向系统当前前台窗口，可操作任意 App。
3. 使用 `adb shell input`、`uiautomator dump`、`dumpsys` 或截图命令兜底。
4. 通过现有 App↔PC reverse 心跳通道请求 App 内无障碍服务执行入口。

脚本不声明或绑定执行环境，同一指令可在不同设备状态下由不同通道完成。四个通道全部失败后才按步骤失败或 `waitState.timeoutPolicy` 处理，并在 PC 配置目录的 `input_failures` 保存现场截图。

`lib_debug_core` 已注册 `ScriptAccessibilityService`。用户在宿主 App 的“PC 通信”面板点击“开启无障碍脚本服务”并完成系统授权后，服务会自动注册 `PcConnectionChecker.InputCommandHandler`，即使宿主 App 不在前台也能执行最后一级兜底。服务沿用当前 reverse 心跳协议，不增加独立端口；未启用服务时该通道明确返回失败。

辅助服务收到的是包含 `sourceWidth`、`sourceHeight`、`targetPackage` 和 `step` 的单步载荷，因此 `source_pixel` 坐标与缺省启动包名在四个通道中保持一致。PC 和 dex agent 的响应超时会按步骤 `waitMs` 或 `durationMs` 自动扩展，最长覆盖 10 分钟条件等待。

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

- `name`：步骤名称，执行进度和日志优先展示该字段。
- `description`：步骤描述；`name` 为空时作为展示文本。
- `delayBeforeMs`：执行前等待。
- `retryCount`：失败重试次数。
- `failurePolicy`：兼容字段，当前版本出现异常后仍会继续尝试后续步骤。

当 `name` 和 `description` 都为空时，PC 工具显示中文步骤类型和 `id`，不会显示 Base64 编码的 agent 协议原文。

## 步骤类型

```json
[
  { "id": "tap", "name": "点击登录按钮", "description": "进入账号登录页", "type": "tap", "x": 320, "y": 640 },
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
  {
    "id": "check",
    "type": "wait",
    "waitMs": 3000,
    "waitState": {
      "conditions": [{ "kind": "foreground", "packageName": "com.example.app" }]
    }
  },
  {
    "id": "node",
    "type": "node_tap",
    "selector": { "resourceId": "com.example.app:id/submit", "text": "提交" }
  }
]
```

`node_tap` 通过 `uiautomator dump` 定位第一个匹配节点。`resourceId`、`text`、`contentDescription`、`className` 中的非空字段同时匹配。

`multi_touch` 使用 touch 模块自动导出的 `frames`，每帧保留完整 `action`、相对时间和全部 pointer。原生多指注入失败时，会把每个 pointer 的轨迹依次降级为 `input swipe`；该兜底只能保证每条轨迹都被尝试，不能保持多指同步语义。

## LQITS v2 条件等待

没有 `waitState` 时，`waitMs` 仍是固定等待时长。存在 `waitState` 时，`waitMs` 是该步骤单次条件等待的总超时时间；执行器先立即检查一次，未满足再按 `pollIntervalMs` 轮询。任意执行通道监听命中后都会固定等待 600ms 再执行后续步骤，该延迟不提供脚本配置。

```json
{
  "magic": "LQITS",
  "version": 2,
  "scriptId": "conditional_wait",
  "sourceWidth": 1080,
  "sourceHeight": 2400,
  "steps": [
    {
      "id": "wait_login",
      "type": "wait",
      "waitMs": 10000,
      "timeoutPolicy": "jump",
      "jumpTargetId": "open_home",
      "waitState": {
        "pollIntervalMs": 250,
        "match": "all",
        "consecutiveMatches": 2,
        "maxTimeoutCount": 10,
        "conditions": [
          {
            "kind": "ui_attribute",
            "selector": { "resourceId": "com.example:id/status" },
            "attribute": "text",
            "operator": "regex",
            "value": "ready|done",
            "ignoreCase": true
          }
        ]
      }
    },
    { "id": "open_home", "type": "launch_app", "packageName": "com.example" }
  ]
}
```

字段规则：

- v2 的所有步骤必须提供全局唯一的 `id`。
- `waitMs` 范围为 `1..600000`；`pollIntervalMs` 默认 250，范围为 `50..5000`。
- `match` 支持 `all`、`any`；`consecutiveMatches` 默认 1，范围为 `1..10`。
- `retryCount` 必须为 0，条件轮询本身不再叠加步骤重试。
- `timeoutPolicy` 默认 `stop`；`jump` 必须同时提供 `jumpTargetId`，可前跳、回跳或跳到自身，目标步骤会被执行。
- 同一 wait 步骤默认允许累计超时 10 次，第 11 次强制停止；计数不会因该步骤后来成功而清零。
- 整份脚本最多实际开始执行 100 个步骤，准备执行第 101 个步骤时强制停止并提示检查等待条件、jump 和整体流程。
- 暂停时间不计入 `waitMs`；状态采集子进程的执行时间计入 `waitMs`。

### 条件类型

等待节点出现或消失：

```json
{ "kind": "ui_exists", "selector": { "resourceId": "com.example:id/loading" }, "expected": false }
```

等待节点属性：

```json
{
  "kind": "ui_attribute",
  "selector": { "contentDescription": "登录状态" },
  "attribute": "selected",
  "operator": "equals",
  "value": "true",
  "ignoreCase": true
}
```

属性支持 `text/contentDescription/selected/checked/enabled/focused`，操作符支持 `equals/not_equals/contains/regex`。默认忽略大小写；正则使用 Java Pattern 的 `find()`。

等待前台应用或 Activity：

```json
{
  "kind": "foreground",
  "packageName": "com.google.android.youtube",
  "activityName": "com.google.android.youtube.app.honeycomb.Shell$HomeActivity"
}
```

`packageName` 必填，`activityName` 可省略。

等待截图区域达到目标颜色占比：

```json
{
  "kind": "region_color",
  "coordinateMode": "normalized",
  "left": 0.35,
  "top": 0.30,
  "right": 0.65,
  "bottom": 0.40,
  "operator": "target_ratio",
  "color": "#34A853",
  "tolerance": 12,
  "minMatchRatio": 0.65,
  "sampleStride": 2
}
```

颜色条件不查找 UI 控件，通过当前执行通道的截图能力取整屏后裁剪和采样。首期仅支持 `target_ratio`，忽略 alpha；坐标模式支持 `normalized/source_pixel/pixel`。辅助服务截图要求 Android 11（API 30）及以上；更低版本会继续保留其他通道的执行结果。

## touch 导出

```java
EventReplayManager manager = new EventReplayManager();
manager.startRecording();
// 执行待记录操作
manager.stopRecording();
manager.saveInputScript(outputFile);
```

`TouchRestorePlugin` 的录制按钮关闭时，也会自动保存到应用外部缓存目录的 `.v/scripts`。
