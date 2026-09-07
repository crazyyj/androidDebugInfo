# app_pc_debug_tools

## Build

在工程根目录执行：

```bash
./gradlew :app_pc_debug_tools:compileKotlinJvm
./gradlew :app_pc_debug_tools:packageDistributionForCurrentOS
./gradlew :app_pc_debug_tools:createDistributable
```

## Desktop Packages

macOS:

```bash
./gradlew :app_pc_debug_tools:packageDmg
```

Windows:

```bash
./gradlew :app_pc_debug_tools:packageMsi
./gradlew :app_pc_debug_tools:packageExe
```

Linux:

```bash
./gradlew :app_pc_debug_tools:packageDeb
./gradlew :app_pc_debug_tools:packageRpm
```

## Clean

```bash
./gradlew :app_pc_debug_tools:clean
```
