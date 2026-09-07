# PC 端应用列表改造 — 提示词

> 本文档面向 AI 编程助手。改造对象是 `app_pc_debug_tools`（PC 端，Kotlin Multiplatform + Compose Desktop）中设备详情面板的「应用列表」。实施前请先读「现状」一节，确保改动建立在准确认知之上。

---

## 一、现状（务必先理解，避免重复造轮子）

### 1. 应用列表的数据来源与缓存
- **拉取**：`PackageInspector.loadInstalledApps(executor, deviceId)`（`app_pc_debug_tools/src/commonMain/kotlin/com/newchar/debug/pc/device/PackageInspector.kt`）。
  - 先 `adb -s <id> shell pm list packages -f -U` 取包列表，过滤 `android` / `com.android.*` / `android.*`；
  - 再并发（`Semaphore(4)`）对每个包执行 `dumpsys package <name>` 补全版本号、安装时间、是否 debuggable 等；
  - 返回 `List<InstalledAppInfo>`，按 `categoryPriority`（Debug > 非系统 > 系统）再按包名排序。
- **数据模型**：`InstalledAppInfo`（`.../device/InstalledAppInfo.kt`），字段：`packageName / apkPath / uid / versionName / versionCode / firstInstallTime / lastUpdateTime / installerPackageName / isSystemApp / isDebuggable`，派生属性 `categoryPriority`。**当前未加 `@Serializable`。**
- **内存缓存**：`AppContent.kt` 顶层 `val packageCache = remember { mutableStateMapOf<String, List<InstalledAppInfo>>() }`，以 `deviceId` 为 key。
- **加载触发**：`DeviceDetailPanel`（`AppContent.kt` 内私有 Composable）的 `LaunchedEffect` 中调用 `PackageInspector.loadInstalledApps`，结果写入 `localPackageList` 与 `packageCache[dev.id]`。设备离线时读 `packageCache[dev.id]` 作为回退。

### 2. 当前 UI（需改造的核心）
- `PackageListPanel`（`AppContent.kt`，`PanelCard` 包裹）渲染列表，但**不是滚动列表**：
  ```kotlin
  packages.take(40).forEach { app -> PackageRow(app = app) }
  if (packages.size > 40) { AppText("仅展示前 40 项...") }
  ```
  即硬编码只渲染前 40 项，超出的看不到。`PanelCard` 本身没有 `verticalScroll`。
- `PackageRow`（`AppContent.kt`）：展示包名、版本/uid、apkPath，右上角 `Debug/System/App` 徽标。无任何已卸载/新增概念。

### 3. 可用的持久化与序列化能力
- **配置目录**：`DesktopAppSettingsStore.configDirectoryPath()`（`app_pc_debug_tools/src/jvmMain/kotlin/com/newchar/debug/pc/config/DesktopAppSettingsStore.kt`）已实现跨平台路径：
  - macOS：`~/Library/Application Support/NewChar/pc-debug-tools`
  - Windows：`%APPDATA%/NewChar/pc-debug-tools`
  - Linux：`~/.config/newchar/pc-debug-tools`
  - **应复用同一目录族**存放应用列表缓存，避免各自为政。
- **两种 IO 风格并存**（择一，保持一致）：
  - `DesktopAppSettingsStore` 用 `kotlinx.io`（`Path` / `SystemFileSystem` / `buffered()` / `readString` / `writeString`），`kotlinx-io-core:0.5.4` 已在 `commonMain` 依赖。
  - `LogcatCollector` 用 `java.io.File` / `FileOutputStream` / `OutputStreamWriter`（`jvmMain`，纯 JDK）。
- **序列化**：`kotlinx-serialization-json:1.6.3` 已在 `commonMain` 依赖，可直接用。注意 `InstalledAppInfo` 当前**没有 `@Serializable`**，需要补。
- **gzip**：JVM 原生 `java.util.zip.GZIPOutputStream` / `GZIPInputStream`（`jvmMain` 可用，无需新依赖）。

---

## 二、改造目标（共四项）

### 目标 1：列表改为可滚动
将 `PackageListPanel` 内的列表从「`take(40) + forEach`」改为**可滚动列表**，展示全部包，不再截断。

### 目标 2：本地缓存 + gzip 压缩 + 记录时间戳
- 把每次拉取到的应用列表**缓存到本地磁盘**（gzip 压缩）。
- 缓存内容**记录当前快照时间**（lastUpdatedAt），便于后续比对与展示。
- 缓存按 `deviceId` 维度独立存放。

### 目标 3：新旧列表比对，标记新增 / 已卸载
- 下次打开设备详情、或再次拉取时，把「本地缓存的上一次快照」与「本次拉取（或当前内存）列表」按 `packageName` 做集合比对：
  - **新增**：本次有、上次没有。
  - **已卸载**：上次有、本次没有。
- 合并成一个带状态标记的统一列表交给 UI 渲染。

### 目标 4：已卸载项的 UI
- 已卸载的行整体**置灰**。
- 已卸载行上**增加「重新安装」按钮**。
- 已卸载行上**增加「已卸载」文本标签，并给该文本加颜色外轮廓**（text stroke / border）。

---

## 三、实现要点（逐项落到代码层面）

### 3.1 数据模型与序列化
1. 给 `InstalledAppInfo` 加 `@Serializable`（`import kotlinx.serialization.Serializable`）。该类在 `commonMain`，序列化依赖已在 `commonMain`，无需改 build.gradle。
2. 新建快照模型（建议放 `app_pc_debug_tools/src/commonMain/kotlin/com/newchar/debug/pc/device/PackageSnapshot.kt`）：
   ```kotlin
   @Serializable
   data class PackageSnapshot(
       val deviceId: String,
       val capturedAt: Long,                 // System.currentTimeMillis()，记录当前快照时间
       val apps: List<InstalledAppInfo>,
   )
   ```
3. 新建带状态标记的展示模型（同目录），供 UI 消费：
   ```kotlin
   enum class PackageStatus { CURRENT, NEW_INSTALLED, UNINSTALLED }

   data class PackageDisplayItem(
       val info: InstalledAppInfo,
       val status: PackageStatus,
   )
   ```

### 3.2 持久化层（新建 `PackageSnapshotStore`）
建议放 `app_pc_debug_tools/src/jvmMain/kotlin/com/newchar/debug/pc/device/PackageSnapshotStore.kt`：
1. **目录**：复用 `DesktopAppSettingsStore.configDirectoryPath()` 同一族目录，子目录 `packages/`，文件名 `packages_<deviceId>.json.gz`（deviceId 含冒号等特殊字符，需做安全编码，如 URL encode 或把 `:` 替换为 `_`）。
2. **写**：`Json.encodeToString(PackageSnapshot.serializer(), snapshot)` → UTF-8 bytes → `GZIPOutputStream` → 落盘。用 `Dispatchers.IO`。
3. **读**：`GZIPInputStream` → 读 bytes → `Json.decodeFromString`。文件不存在或解析失败返回 `null`（不要崩溃）。
4. 建议 IO 风格与 `LogcatCollector` 一致用 `java.io`（gzip 也在 `java.util.zip`），避免在 `kotlinx.io` 与 JDK 流之间来回转换。
5. 提供 `suspend fun save(snapshot)` 与 `suspend fun load(deviceId): PackageSnapshot?`。

### 3.3 比对逻辑
新建纯函数（可在 `PackageSnapshot.kt` 内或 `PackageDiff.kt`）：
```kotlin
fun diff(prev: List<InstalledAppInfo>, curr: List<InstalledAppInfo>): List<PackageDisplayItem> {
    val prevNames = prev.map { it.packageName }.toSet()
    val currNames = curr.map { it.packageName }.toSet()
    val currItems = curr.map { PackageDisplayItem(it, if (it.packageName !in prevNames) PackageStatus.NEW_INSTALLED else PackageStatus.CURRENT) }
    val uninstalledItems = prev.filter { it.packageName !in currNames }
        .map { PackageDisplayItem(it, PackageStatus.UNINSTALLED) }
    // 排序：新增优先 > 当前(按原 categoryPriority) > 已卸载；或按需把已卸载单列。保持与现有 categoryPriority 一致的优先级。
    return (currItems + uninstalledItems).sortedWith(...)
}
```
- 比对维度：`packageName`（唯一键）。
- 首次（无缓存）：所有项 `CURRENT`，无新增/卸载。

### 3.4 接入加载流程
修改 `DeviceDetailPanel`（`AppContent.kt`）里那段 `LaunchedEffect`：
1. 先 `PackageInspector.loadInstalledApps(...)` 得到当前 `curr`。
2. 从 `PackageSnapshotStore.load(dev.id)` 读上次快照 `prev`。
3. `diff(prev?.apps ?: emptyList(), curr)` 得展示列表。
4. 写新快照 `PackageSnapshotStore.save(PackageSnapshot(dev.id, now, curr))`（**保存的是本次拉取的「当前」全量，不含已卸载项**，否则卸载项会无限累积）。
5. `packageCache[dev.id]` 仍存 `curr`（或改存展示列表，视 UI 取用而定，二选一并保持一致）。
- 设备离线时：用 `prev` 快照渲染（已卸载项此时也可显示为灰色，符合「设备没接时也能看到历史」的预期）。

### 3.5 UI 改造（`PackageListPanel` / `PackageRow`）
1. **可滚动**：`PackageListPanel` 内 `PanelCard` 中，用 `LazyColumn`（Compose 已支持，注意不要嵌套在另一个无界高度的 `Column` 滚动里；可给 `LazyColumn` 一个固定 `heightIn(max = ...)` 或 `fillMaxHeight`）。移除 `take(40)` 与「仅展示前 40 项」提示。
   - 若 `PanelCard` 外层是 `verticalScroll` 的 `Column`，`LazyColumn` 嵌套会抛 `Vertically scrollable component was measured with an infinity maximum height`。解决办法：给 `LazyColumn` 加 `Modifier.heightIn(max = 480.dp)` 之类固定上限，或重构面板布局把应用列表提到独立可滚动区域。
2. **传入状态**：`PackageRow(app, status: PackageStatus)`。
3. **已卸载项**（`status == UNINSTALLED`）：
   - 整行 alpha 降低/文字色置灰（如 `Modifier.graphicsLayer { alpha = 0.45f }`，或文字用 `AppTheme.textHint` 灰色）。
   - 右侧增加「**重新安装**」按钮（复用 `AppSmallButton` / `AppSmallOutlinedButton`）。
   - 增加「**已卸载**」文字标签，**带颜色外轮廓**。
4. **新增项**（`status == NEW_INSTALLED`，可选增强）：建议加「新增」徽标（如绿色），与「已卸载」对称。

### 3.6「重新安装」按钮的实现策略（⚠️ 品牌路由应用商店）
应用被卸载后，设备上 `apkPath` 已不存在，缓存里的 `apkPath` 不可用。**按需求：先识别设备品牌，再根据「品牌 + 包名」通过 adb 广播指令跳转到该品牌对应的应用商店详情页**。

1. **获取设备品牌**：品牌信息在设备扫描阶段已解析，存于 `DeviceInfo.manufacturer`（厂商，如 `Huawei` / `Xiaomi` / `OPPO` / `vivo` / `samsung`），`DeviceDetailPanel` 接收的 `device: DeviceInfo` 已含此字段，**直接取用即可，无需重复查询**。
   - 字段缺失兜底：`adb -s <id> shell getprop ro.product.brand` / `ro.product.manufacturer`。
2. **品牌 → 应用商店映射表**（建议新建 `app_pc_debug_tools/src/commonMain/.../device/AppStoreLauncher.kt`，纯函数，`brand(manufacturer, pkg) -> command`）：

   | 品牌关键字（manufacturer 小写包含） | 商店应用包名 | deeplink |
   |---|---|---|
   | `huawei` / `honor` | com.huawei.appmarket | `appmarket://details?id=<pkg>` |
   | `xiaomi` / `redmi` | com.xiaomi.market | `mimarket://details?id=<pkg>` |
   | `oppo` | com.heytap.market | `market://details?id=<pkg>` |
   | `vivo` / `bbk` | com.bbk.appstore | `market://details?id=<pkg>` |
   | `samsung` | com.sec.android.app.samsungapps | `samsungapps://ProductDetail/<pkg>` |
   | 其它 / 未命中 | —（通用兜底） | `market://details?id=<pkg>` |

3. **跳转指令**（PC 端经 `executor.shell(deviceId, ...)` 或 `executor.adb(...)` 发给目标设备，等价于「广播跳转」）：
   - 默认用 **`am start`**（VIEW intent）拉起商店详情页，这是各品牌商店公开支持、最通用的方式：
     ```
     adb -s <id> shell am start -a android.intent.action.VIEW -d "<scheme>://details?id=<pkg>"
     ```
   - 若某品牌商店需经特定广播进入（厂商定制 action），用 **`am broadcast`** 补充，action/extra 以厂商公开文档为准。优先 `am start`，`am broadcast` 仅作特定品牌兜底。
   > 说明：用户表述为「使用广播跳转」，在 adb 层统一以 `am` 指令（`am start` 为主、`am broadcast` 为特定品牌兜底）实现「PC 端发指令 → 设备端打开对应应用商店详情页」。
4. **系统应用特殊处理**：`isSystemApp == true` 的「已卸载」通常是禁用而非真删除，优先用 `adb -s <id> shell pm enable <pkg>` 恢复，而非跳商店。
5. **点击反馈**：点击后用现有 `onToast` 提示「正在跳转 <品牌> 应用商店」；商店未安装 / 跳转失败时 catch 异常并 toast 提示「该品牌应用商店不可用」。

### 3.7「已卸载」文字外轮廓的实现（⚠️ Compose 无原生文字 stroke）
Compose `BasicText` 不直接支持文字描边。可行做法（任选其一，代码注释说明）：
- **多层描边法（推荐，无依赖）**：在 `Box` 中叠加 8 方向偏移（上下左右+对角，偏移 1~2px）的 `BasicText`，颜色为描边色；最上层放正常文字色。效果即文字描边。
- **drawStyle = Stroke**（Compose 较新版本支持 `TextStyle` 配合 `drawStyle = Stroke(width)`，再叠一层 `Fill` 文字）：若当前 Compose 版本支持，用这个更干净。
- 描边色建议用警示色（如 `AppTheme.danger` 的橙/红），与置灰背景形成对比。

---

## 四、涉及文件清单

| 文件 | 改动 |
|---|---|
| `app_pc_debug_tools/src/commonMain/.../device/InstalledAppInfo.kt` | 加 `@Serializable` |
| `app_pc_debug_tools/src/commonMain/.../device/PackageSnapshot.kt` | **新建**：快照模型 + `PackageStatus` 枚举 + `PackageDisplayItem` + `diff()` 纯函数 |
| `app_pc_debug_tools/src/jvmMain/.../device/PackageSnapshotStore.kt` | **新建**：gzip + JSON 持久化，复用 `configDirectoryPath()` 目录族 |
| `app_pc_debug_tools/src/jvmMain/.../pc/AppContent.kt` | `PackageListPanel` 改 `LazyColumn` 可滚动；`PackageRow` 加 `status` 参数与已卸载 UI；`DeviceDetailPanel` 的 `LaunchedEffect` 接入「读缓存→diff→存快照」流程；新建 store 实例（可复用 `settingsStore` 所在作用域） |
| `app_pc_debug_tools/src/jvmMain/.../config/DesktopAppSettingsStore.kt` | 可选：把 `configDirectoryPath()` 抽成共享工具，供 `PackageSnapshotStore` 复用（避免目录逻辑重复） |
| `app_pc_debug_tools/src/commonMain/.../device/AppStoreLauncher.kt` | **新建**：`brand(manufacturer, pkg) -> command` 品牌路由映射表 + 跳转指令生成 |
| `app_pc_debug_tools/src/commonMain/.../device/DeviceInfo.kt` | 只读：取 `manufacturer` 字段（设备扫描阶段已解析） |

> 不需要改 `build.gradle.kts`（序列化、协程、IO 依赖均已存在；gzip 用 JDK 原生）。

---

## 五、验收标准

- [ ] 应用列表完整展示全部包，列表区域可纵向滚动，不再有「仅展示前 40 项」截断。
- [ ] 拉取应用列表后，本地生成 `packages_<deviceId>.json.gz`，文件确实是 gzip 压缩（解压后为合法 JSON，含 `capturedAt` 时间戳）。
- [ ] 关闭并重新打开应用、再次选中同一设备时：读缓存并与新列表比对，**新增**包被标记，**已卸载**包被标记并显示出来。
- [ ] 在设备上手动卸载一个应用后再次拉取，该包行变灰、出现「已卸载」描边文字标签与「重新安装」按钮。
- [ ] 点「重新安装」先取设备 `manufacturer`，按品牌路由到对应应用商店详情页（华为/小米/OPPO/vivo/三星/通用兜底），有 toast 反馈；系统应用用 `pm enable` 恢复；商店不可用时 catch 异常并 toast 提示，不崩溃。
- [ ] 「已卸载」文字有可见的颜色外轮廓。
- [ ] 设备离线时，仍能从缓存渲染上次列表（含历史卸载标记）。
- [ ] 多设备场景下缓存互不串扰（按 deviceId 隔离）。
- [ ] 缓存文件损坏/不可读时优雅降级，不影响主流程。

---

## 六、给 AI 的实现顺序建议

1. 先给 `InstalledAppInfo` 加 `@Serializable` 并编译通过。
2. 新建 `PackageSnapshot` / `PackageStatus` / `PackageDisplayItem` 与 `diff()`，**写单元测试**验证 diff 逻辑（新增/卸载/首次）。
3. 新建 `PackageSnapshotStore`，验证 gzip 往返读写。
4. 改 `DeviceDetailPanel` 的加载流程接入 store + diff。
5. 改 `PackageListPanel` 为 `LazyColumn`，改 `PackageRow` 支持 `status` 与已卸载 UI（描边文字 + 重新安装按钮）。
6. 新建 `AppStoreLauncher` 品牌路由映射，接「重新安装」按钮：取 `device.manufacturer` → 选商店 deeplink → `executor` 发 `am start`/`am broadcast`；系统应用走 `pm enable`。
7. 端到端：在设备上装/卸一个应用，验证整条链路；多品牌设备各测一次跳转目标正确。
