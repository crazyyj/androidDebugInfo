# 需求理解：LQITS 条件等待 waitState

> 来源：用户口述
> 场景：app（PC 工具 + Android dex agent）
> 生成时间：2026-09-17
> 状态：已确认 ｜ 待确认项：0

## 0. 结论与设计原则

本需求不是给现有 `waitMs` 增加几个零散字段，而是让 `wait` 同时支持两种互斥模式：

1. 固定等待：没有 `waitState` 时，继续按 `waitMs` 睡眠，保持现有脚本行为。
2. 条件等待：存在 `waitState` 时，`waitMs` 表示总超时时间；立即检查状态，未满足则按轮询间隔继续检查，条件满足后立即进入下一步。

推荐使用 LQITS v2 表达 `waitState`。旧 v1 执行器必须明确拒绝 v2，不能忽略新字段后直接进入下一步。v1 脚本继续按原逻辑执行，不强制迁移。

为满足“通过 dex app_process 执行、不通过 ADB input”的要求，包含 `waitState` 的脚本必须强制使用 dex agent；agent 启动或运行失败时直接失败，不进入 PC 端 `AdbInputFallbackRunner`。

## 1. 明确改动点

### 1.1 新增功能 / 模块

| # | 功能 | 目标与场景 | 业务规则 | 技术方案 | 数据结构 | UI/交互 |
|---|------|-----------|---------|---------|---------|---------|
| 1 | 条件等待模型 | 等待 UI、文本、状态、颜色、前台应用满足目标状态 | 首次立即检查；满足即结束；超时失败 | PC 与 dex 两端增加同构模型 | `waitState`、`conditions` | PC 日志展示等待中、满足、超时与最后观测值 |
| 2 | 组合条件 | 同一步等待多个条件 | 支持 `all` / `any` | 每轮采样一次状态，再统一计算 | `match` | 无新增页面 |
| 3 | UI 节点状态 | 等待节点出现/消失，或属性变化到目标值 | 节点选择器沿用现有 selector | 复用 uiautomator XML | `ui_exists`、`ui_attribute` | 无新增权限 |
| 4 | 前台状态 | 等待指定包名或 Activity 成为前台 | 包名必填，Activity 可选 | 复用并扩展 dumpsys 解析 | `foreground` | 无新增权限 |
| 5 | 区域颜色状态 | 截图后比较大致矩形范围是否达到目标颜色 | 不依赖 UI 节点；支持 RGB 容差与像素占比 | dex agent 截图并在设备端处理 | `region_color` | 无新增页面 |
| 6 | 严格超时与分支 | 防止无限等待；超时后停止或跳过一段流程 | `waitMs` 是总超时；默认 stop，可 jump 到指定步骤 ID；单个 wait step 默认最多累计超时 10 次，整份脚本最多执行 100 个步骤 | 基于可运行时长递减；跳转前校验目标；执行器维护计数器 | `timeoutPolicy`、`jumpTargetId`、`maxTimeoutCount` | 达到安全上限时提示检查脚本合理性 |

### 1.2 修改内容

| # | 修改前 (`file:line`) | 修改后 | 范围与影响 | 实现路径 |
|---|----------------------|--------|-----------|----------|
| 1 | `app_debug_pc_tools/src/jvmMain/kotlin/com/newchar/debug/pc/device/input/InputScriptDocument.kt:35` 仅有 `waitMs/timeoutMs` | `InputScriptStep` 增加可空 `waitState`，并校验 v1/v2 与字段组合 | PC 文件解析 | 增加可序列化数据类和校验，不改 UI 选择器模型 |
| 2 | `app_debug_pc_tools/agent-src/com/newchar/probe/input/AgentScript.java:45` 手工解析 Step | 解析 `waitState` 与条件数组 | dex JSON 解析 | 增加嵌套模型，保留 v1 字段默认值 |
| 3 | `app_debug_pc_tools/agent-src/com/newchar/probe/input/InputScriptRunner.java:172` 仅 sleep `waitMs` | 有 `waitState` 时进入条件轮询 | dex 执行器 | 固定等待逻辑保持不变；条件等待委托给专用 evaluator |
| 4 | `app_debug_pc_tools/agent-src/com/newchar/probe/input/ShellBridge.java:35` 仅返回前台包名 | 返回前台 component，并提供 UI 快照、节点属性、截图取色能力 | dex 状态采集 | 每轮按需、惰性采集，避免每个条件重复 dump/screencap |
| 5 | `app_debug_pc_tools/src/jvmMain/kotlin/com/newchar/debug/pc/device/input/InputScriptSession.kt:96` agent 失败自动切 ADB | 含 `waitState` 时禁止 ADB fallback | PC 执行链路 | v1 无条件脚本保持原有兜底策略 |
| 6 | `app_debug_pc_tools/agent-src/com/newchar/probe/input/InputScriptRunner.java:53` 线性顺序执行 | waitState 超时后支持 stop 或跳到指定 ID | 超时后的流程安全 | 执行器将目标 ID 解析为步骤下标；jump 后执行目标步骤 |
| 7 | `app_debug_pc_tools/INPUT_SCRIPT.md:55` 只说明固定等待 | 增加 v2、条件类型、超时与示例 | 使用文档 | 保留 v1 文档与兼容说明 |
| 8 | `app_debug_pc_tools/src/jvmTest/kotlin/com/newchar/debug/pc/device/input/InputScriptDocumentTest.kt:8` 无 waitState 用例 | 增加解析、非法组合、版本兼容测试 | 自动测试 | PC 单测 + dex 实机集成验证 |

建议仅新增一个辅助类 `WaitStateEvaluator.java`。它隔离轮询、超时和条件求值，避免继续膨胀 `InputScriptRunner`；其余状态读取仍放在已有 `ShellBridge`，不额外拆分类。

### 1.3 废弃 / 下线

| # | 下线项 | 影响范围 | 老用户兜底 | 清理时间表 |
|---|--------|---------|-----------|-----------|
| 1 | 无 | 无 | v1 固定等待继续支持 | 暂不下线 |

## 2. 推荐 JSON 协议

### 2.1 总体结构

```json
{
  "id": "wait_youtube_login",
  "type": "wait",
  "waitMs": 10000,
  "timeoutPolicy": "jump",
  "jumpTargetId": "open_youtube_home",
  "waitState": {
    "pollIntervalMs": 250,
    "match": "all",
    "consecutiveMatches": 2,
    "maxTimeoutCount": 10,
    "conditions": []
  }
}
```

字段规则：

| 字段 | 必填 | 默认值 | 规则 |
|------|------|--------|------|
| 顶层 `waitMs` | 是 | 无 | waitState 存在时表示总超时，建议范围 `1..600000` |
| 顶层 `timeoutPolicy` | 否 | `stop` | 枚举：`stop`、`jump` |
| 顶层 `jumpTargetId` | jump 时是 | 无 | 指向 steps 中唯一 ID；允许任意方向跳转，目标步骤本身会执行 |
| `pollIntervalMs` | 否 | `250` | 范围 `50..5000`；从一轮结束到下一轮开始的间隔 |
| `match` | 否 | `all` | `all` 表示全部满足，`any` 表示任一满足 |
| `consecutiveMatches` | 否 | `1` | 连续满足 N 次才成功，用于过滤 UI 闪烁；建议范围 `1..10` |
| `maxTimeoutCount` | 否 | `10` | 同一个 wait step 在一次脚本运行中允许累计超时的次数，范围 `1..100`；仅对 jump 流程实际产生影响 |
| `conditions` | 是 | 无 | 非空数组；同一轮使用同一份状态快照 |

`waitMs` 保持原字段不变：没有 `waitState` 时表示固定等待时长；存在 `waitState` 时表示条件等待的总超时时间。`delayBeforeMs` 仍用于开始检查前的固定延迟。

`jump` 不在 JSON 中写数字下标，避免步骤增删后跳错位置。执行器启动前建立 `id -> index` 映射，将 `jumpTargetId` 解析为内部 index。目标 ID 必须存在且唯一；允许向前、向后或跳到自身，跳转后从目标步骤开始执行。

执行器按 step ID 维护 waitState 超时次数，同一步每次完整耗尽 `waitMs` 才累加一次，普通轮询不计数。最多允许发生 `maxTimeoutCount` 次超时并执行相应策略，第 `maxTimeoutCount + 1` 次超时时无论配置何种策略都强制 stop；计数在该步骤后来满足条件时也不清零，直到本次脚本运行结束。整份脚本另外设置固定的 100 次步骤执行上限，所有类型的步骤每开始执行一次计数一次；准备执行第 101 次时强制 stop。两个上限均不新增重试，也不受暂停影响。

### 2.2 UI 出现或消失

```json
{
  "kind": "ui_exists",
  "selector": { "resourceId": "com.example:id/loading" },
  "expected": false
}
```

`expected=true` 等待出现，`false` 等待消失。selector 复用现有 `resourceId/text/contentDescription/className` 且全部非空字段为 AND。

### 2.3 UI 属性达到目标值

```json
{
  "kind": "ui_attribute",
  "selector": { "resourceId": "com.example:id/status" },
  "attribute": "text",
  "operator": "equals",
  "value": "安装完成",
  "ignoreCase": true
}
```

首期属性限定为 `text/contentDescription/selected/checked/enabled/focused`；操作符支持 `equals/not_equals/contains/regex`。文本比较默认 `ignoreCase=true`，可显式设为 false。布尔属性的 value 使用 `"true"` 或 `"false"`。

`regex` 使用 Java Pattern，匹配方式采用 `find()`，默认开启 `CASE_INSENSITIVE | UNICODE_CASE`。正则在脚本解析阶段预编译，非法表达式直接拒绝执行；建议限制表达式长度，降低灾难性回溯风险。

### 2.4 前台应用或页面

```json
{
  "kind": "foreground",
  "packageName": "com.google.android.youtube",
  "activityName": "com.google.android.youtube.app.honeycomb.Shell$HomeActivity"
}
```

`packageName` 必填；`activityName` 为空时只比较包名。这里的“桌面前台”按“设备当前前台应用/Activity”理解。

### 2.5 截图区域颜色

颜色条件不寻找 UI 控件。dex agent 使用当前可用的 shell 截图能力获取整屏图像，再按脚本配置的大致矩形范围裁剪和比较。坐标支持 `normalized/source_pixel/pixel`，推荐 normalized 适配不同分辨率。

目标颜色占比示例：

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

首期仅支持 `target_ratio`：区域内与目标颜色近似的采样像素达到指定比例即满足条件。`tolerance` 是单个 RGB 通道差值阈值；`sampleStride` 用于隔点采样以降低 CPU 成本。暂不实现相对初始截图变色、UI 节点取色、OCR 或图像相似度。

截图优先在 dex agent 内通过 shell UID 调用 `screencap`，并在设备端解码和比较，不把每一帧传回 PC。这样改动范围最小，也避免条件等待依赖 PC 端截图链路。若设备端截图不可用，该条件返回采集失败；不切换到 ADB input。

## 3. 执行语义

1. 执行 `delayBeforeMs`。
2. 若无 `waitState`，按原逻辑等待 `waitMs` 后成功。
3. 若有 `waitState`，先立即采样一次，不先 sleep。
4. 单轮中按条件需要最多执行一次 UI dump、一次前台查询、一次 screencap。
5. 对 `conditions` 应用 `all/any`，满足后累计 `consecutiveMatches`；任何一轮不满足则清零。
6. 满足目标次数后返回 `STEP_OK`，backend 记为 `wait_state`，立即执行下一步。
7. 累计可运行时间达到 `waitMs` 后触发超时策略，日志包含耗时、未满足条件和最后观测值。
8. `timeoutPolicy=stop`（默认）时终止脚本并报告 FAILED。
9. `timeoutPolicy=jump` 时把下一执行位置改为 `jumpTargetId` 对应 index；允许任意方向跳转，目标步骤本身会执行。该分支属于预期控制流，不计入最终失败数。
10. 每次 waitState 耗尽 `waitMs` 时，按 step ID 累加超时次数；前 10 次允许执行配置的策略，第 11 次强制 stop，并提示检查该步骤的等待条件和 jump 配置是否合理。
11. 每开始执行一个步骤，整份脚本累计执行次数加一；最多允许 100 次，准备执行第 101 次时强制 stop，并提示检查脚本跳转、等待条件及整体流程是否合理。
12. 暂停期间不扣减 `waitMs`；取消应在当前采样命令结束或命令超时后尽快退出。
13. `retryCount` 对 waitState 必须为 0；轮询本身已经是重试，避免总耗时意外倍增。

为保证严格超时，`uiautomator dump`、`dumpsys` 和 `screencap` 子进程自身也必须设置单次命令超时，并计入总 `waitMs`。否则外层计时无法阻止某次系统命令永久阻塞。

## 4. 潜在改动点（连带 / 依赖）

- [ ] jump 需要步骤 ID 唯一校验、目标存在校验和双层循环保护：单个 wait step 默认最多累计超时 10 次，整份脚本最多执行 100 个步骤 — 关联：PC/agent 解析器与 runner。
- [ ] PC 状态页目前只显示一步的最终消息；建议等待过程中低频上报剩余时间和最后状态，但限制为每秒最多一次 — 关联：监控窗口。
- [ ] 条件等待使用 uiautomator 时，系统动画、软键盘和 overlay 可能改变节点树 — 关联：UI 适配。
- [ ] Compose/自绘控件可能不暴露 text/selected 等属性，只能退化到 contentDescription 或颜色 — 关联：目标应用可访问性。
- [ ] 部分 ROM 的 `dumpsys activity` 输出格式不同，需要 vivo/AOSP/小米等样本 — 关联：前台判断。
- [ ] screencap 每 250ms 执行成本较高；只有包含颜色条件时才启用，并建议颜色轮询间隔不低于 500ms — 关联：性能与耗电。
- [ ] 动态颜色、暗色模式、透明度、色彩空间和视频内容会影响区域像素比例 — 关联：颜色容差与阈值。
- [ ] v2 脚本必须与支持 waitState 的 dex 同步发布，旧 PC/agent 不可静默执行 — 关联：版本兼容。
- [ ] 含 waitState 的脚本禁止 ADB fallback，否则状态判断语义可能丢失或重复执行前序步骤 — 关联：执行恢复。
- [ ] 失败截图应保留，但需避免截图中账号、通知等敏感信息长期留存 — 关联：数据安全。

## 5. 跨端协作与联调计划

### 5.1 客户端（多端）

| 端 | 页面/组件 | 改动范围 (`file:line`) | 端能力/权限 | 备注 |
|----|----------|-----------------------|-------------|------|
| PC/JVM | 脚本解析、监控、fallback 决策 | `InputScriptDocument.kt:35`、`InputScriptSession.kt:96` | ADB 仅用于推送/启动 app_process | 不在 PC 端重复实现 waitState evaluator |
| Android dex | JSON 解析、状态采集、条件轮询 | `AgentScript.java:45`、`InputScriptRunner.java:172`、`ShellBridge.java:35` | shell UID、uiautomator、dumpsys、screencap | 核心执行端 |
| Android touch 模块 | 无改动 | `InputScript.java:57` | 无 | 现阶段只导出手势，不生成 wait 步骤 |

#### 端覆盖矩阵

- 优先覆盖 Android API 27+，先验证当前 vivo_1820，再验证 AOSP 新版本。
- 不新增 Android Manifest 权限、不注册 AccessibilityService。
- UI 文本条件天然受语言影响；推荐优先使用 resourceId，其次 contentDescription，最后 text。
- 暗色模式会影响 target_ratio；需要分别配置目标颜色，或通过多个 `region_color` 条件配合 `match=any` 兼容主题差异。

### 5.2 服务端接口与中间件

无服务端、数据库、缓存或网络接口改动。

### 5.3 联调方案

- 准备一个 v1 固定等待脚本，验证行为完全不变。
- 准备 v2 脚本覆盖 UI 出现/消失、文本、selected、前台包、前台 Activity和区域目标色占比。
- 每类条件验证：立即满足、延迟满足、始终不满足、暂停后恢复、执行中取消、状态命令超时。
- 构造回跳循环，验证单个 wait step 前 10 次超时按策略执行、第 11 次强制停止；再验证脚本最多执行 100 个步骤，第 101 次执行前停止。
- 检查 agent 回传 backend 必须为 `wait_state`，含 waitState 的脚本不得出现 `adb_input`。
- 以真实 Google Play / YouTube 流程验证“条件满足才进入下一步”，替换当前大量固定 `waitMs`。

## 6. 实施与验收标准

### 6.1 开发顺序

1. 先增加 v2 PC/agent 数据模型与严格校验。
2. 实现 UI 与前台两类条件，完成轮询、暂停、取消、超时。
3. 实现颜色采样，并加入性能限制。
4. 禁用 waitState 脚本的 ADB fallback，落实 stop 策略。
5. 更新文档与示例脚本，重建 `input_tool.dex`。
6. 运行 JVM 测试、agent 编译及真机端到端测试。

### 6.2 验收标准

- v1 固定 wait 行为与现状一致。
- v2 条件已满足时，首次检查后立即通过，不额外等待完整 timeout。
- 条件延迟满足时，下一步骤不得提前执行。
- 条件始终不满足时，在 `waitMs + 单次命令容差` 内触发 stop 或 jump。
- `all/any` 和连续匹配次数符合定义。
- 暂停 5 秒不会消耗条件等待的超时预算。
- 取消后不执行后续步骤。
- `timeoutPolicy=stop` 超时后脚本终态为 FAILED；jump 会执行目标步骤且不计为失败。
- jump 目标不存在或 ID 重复时，脚本必须在执行前校验失败；前跳、后跳和跳到自身均应正确执行目标步骤。
- 同一个 wait step 前 10 次超时允许执行配置策略，第 11 次超时时必须强制停止；计数不得因该步骤中途满足过条件而清零。
- 整份脚本最多执行 100 个步骤；准备执行第 101 次时必须停止。两种上限提示都要包含当前计数、step ID，并明确提示检查等待条件、jump 和整体流程合理性。
- 多个 UI 条件同轮只产生一次 UI dump；多个颜色条件同轮只产生一次 screencap。
- 含 waitState 的脚本在 agent 启动失败时不得切换 ADB input。
- 日志不得只写“超时”，必须给出未满足条件及最后观测值。

### 6.3 性能基线

| 指标 | 目标 | 当前基线 | 风险 |
|------|------|---------|------|
| UI 条件轮询 | 默认 250ms，单轮一次 dump | 待测 | uiautomator 在老设备可能超过 1 秒 |
| 区域颜色轮询 | 建议不低于 500ms，按 stride 抽样 | 待测 | screencap/解码产生 CPU 与内存压力 |
| 内存 | 单张 Bitmap 使用后立即 recycle/释放 | 待测 | 全屏截图可能造成峰值内存 |
| dex 大小 | 增量建议小于 100KB | 当前 input_tool.dex 约 28KB | 引入图像库会显著膨胀，禁止新增第三方库 |

弱网不影响本地状态采集，但目标 App 的网络加载可能导致超时；脚本作者应按业务页面最慢可接受时间配置 timeout。

### 6.4 发版策略

- PC 工具与内置 `input_tool.dex` 同版本发布；dex 继续按内容哈希推送。
- v1/v2 同时支持，便于回滚；回滚到旧工具时 v2 脚本应被版本校验拒绝。
- 不涉及应用商店发版；属于内部 PC 工具和调试 dex 更新。

## 7. 风险识别

- 技术风险：uiautomator dump 本身可能慢或返回空树；必须允许下一轮重试，但不能绕过总超时。
- 技术风险：颜色不是语义状态，主题、亮度、视频帧、抗锯齿均会造成误判；需要容差、区域比例和连续命中过滤。
- 技术风险：目标节点重复时默认取第一个，可能匹配错误；后续可扩展 index，但首期不增加。
- 兼容风险：如果沿用 v1 并让旧 agent 忽略 waitState，会造成后续操作提前执行，因此推荐 v2。
- 流程风险：jump 目标配置错误会跳过必要步骤或形成循环，因此要求目标 ID 唯一，并设置单 wait step 超时次数与整份脚本步骤执行次数两层上限。

### 7.1 合规与审核

- 不新增权限、SDK、用户数据采集或网络上传。
- UI dump 与失败截图可能包含账号、通知等信息；继续沿用本地存储，并建议增加保留时长或手动清理说明。
- 不涉及 Google Play 商店审核与数据安全表单更新。

## 8. 输入输出与数据规则

| # | 功能/接口 | 输入 | 输出 | 数据规则 | 待确认 |
|---|----------|------|------|---------|--------|
| 1 | 固定 wait | `waitMs >= 0` | STEP_OK/CANCELLED | 与 v1 一致 | 否 |
| 2 | 条件 wait | 非空 conditions、`waitMs` | STEP_OK/FAILED/JUMP | 立即检查、严格总超时 | 否 |
| 3 | UI 属性 | selector、attribute、operator、value | 当前属性与匹配结果 | 默认忽略大小写，支持 regex | 否 |
| 4 | 前台状态 | packageName、可选 activityName | 当前 component 与匹配结果 | 包名精确匹配 | 否 |
| 5 | 区域颜色 | 矩形、`target_ratio`、阈值、采样步长 | 目标色像素比例 | 截图后设备端抽样比较，忽略 alpha；首期不支持 changed_ratio | 否 |
| 6 | timeout | `waitMs=1..600000ms`、策略、`maxTimeoutCount` | 超时原因、最后值、当前超时次数 | 默认 stop；jump 指向任意唯一 ID；同一步默认允许累计超时 10 次，第 11 次强制停止 | 否 |
| 7 | 脚本执行保护 | 每次实际开始执行的 step | 当前总执行次数 | 全脚本最多执行 100 个步骤，准备执行第 101 次时强制停止并提示检查合理性 | 否 |

### 8.1 埋点与指标

不新增业务埋点。PC 日志建议记录 wait 类型、实际等待时长、轮询次数、超时条件，便于调试但不上传。

### 8.2 A/B 实验

不涉及。

## 9. 待确认问题清单

无。当前计数口径已确认：单个 wait step 默认最多累计超时 10 次；整份脚本最多执行 100 个步骤。