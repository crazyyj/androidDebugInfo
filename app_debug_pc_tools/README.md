# app_debug_pc_tools

## Build

在工程根目录执行：

```bash
./gradlew :app_debug_pc_tools:compileKotlinJvm
./gradlew :app_debug_pc_tools:packageDistributionForCurrentOS
./gradlew :app_debug_pc_tools:createDistributable
```

## Desktop Packages

macOS（在 Mac 上使用 JDK 17，于工程根目录执行）：

编译并生成可直接运行的 `.app`：

```bash
./gradlew :app_debug_pc_tools:createDistributable
```

产物目录：`app_debug_pc_tools/build/compose/binaries/main/app/`。

生成 DMG 安装包（会自动完成编译）：

```bash
./gradlew :app_debug_pc_tools:packageDmg
```

产物目录：`app_debug_pc_tools/build/compose/binaries/main/dmg/`。

开发时直接运行：

```bash
./gradlew :app_debug_pc_tools:run
```

Windows:

```bash
./gradlew :app_debug_pc_tools:packageMsi
./gradlew :app_debug_pc_tools:packageExe
```

Linux:

```bash
./gradlew :app_debug_pc_tools:packageDeb
./gradlew :app_debug_pc_tools:packageRpm
```

## Clean

```bash
./gradlew :app_debug_pc_tools:clean
```