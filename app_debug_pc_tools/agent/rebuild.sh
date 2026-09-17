#!/usr/bin/env bash
# 重建设备代理 dex（ncprobe.dex、wifi_tool.dex、input_tool.dex）。
#
# 用法：
#   ./agent/rebuild.sh [<android.jar> [<d8>]]
#
# 默认：
#   - android.jar -> ${ANDROID_HOME:-~/Library/Android/sdk}/platforms/android-33/android.jar
#   - d8          -> ${ANDROID_HOME:-~/Library/Android/sdk}/build-tools/34.0.0/d8
#
# 重新构建后，把生成的 dex 同时拷贝到
#   src/jvmMain/resources/agent/ncprobe.dex、wifi_tool.dex 与 input_tool.dex
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK_ROOT="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ANDROID_JAR="${1:-$SDK_ROOT/platforms/android-33/android.jar}"
D8_BIN="${2:-$SDK_ROOT/build-tools/34.0.0/d8}"

if [[ ! -f "$ANDROID_JAR" ]]; then
    echo "[rebuild] android.jar 不存在: $ANDROID_JAR" >&2
    exit 1
fi
if [[ ! -x "$D8_BIN" ]]; then
    echo "[rebuild] d8 不存在或不可执行: $D8_BIN" >&2
    exit 1
fi

# 找一个能用的 javac（JDK 17+）。
JAVAC_BIN="$(command -v javac || true)"
if [[ -z "$JAVAC_BIN" ]]; then
    for jdk in \
        /usr/libexec/java_home \
        "$HOME/Library/Java/JavaVirtualMachines" ; do
        if [[ -x "$jdk/bin/javac" ]]; then
            JAVAC_BIN="$jdk/bin/javac"
            break
        fi
    done
fi
if [[ -z "$JAVAC_BIN" ]]; then
    echo "[rebuild] 找不到 javac，请在 PATH 中提供 JDK" >&2
    exit 1
fi

OUT_DIR="$ROOT/build/agent"
BASE_CLASS_DIR="$OUT_DIR/base-classes"
BASE_DEX_DIR="$OUT_DIR/base-dex"
INPUT_CLASS_DIR="$OUT_DIR/input-classes"
INPUT_DEX_DIR="$OUT_DIR/input-dex"
mkdir -p "$BASE_CLASS_DIR" "$BASE_DEX_DIR" "$INPUT_CLASS_DIR" "$INPUT_DEX_DIR"

echo "[rebuild] 编译基础 agent"
"$JAVAC_BIN" -nowarn --release 8 -cp "$ANDROID_JAR" -d "$BASE_CLASS_DIR" \
    "$ROOT/agent-src/com/newchar/probe/Main.java" \
    "$ROOT/agent-src/com/newchar/probe/wifi/WifiToolMain.java"
"$D8_BIN" --min-api 27 --output "$BASE_DEX_DIR" $(find "$BASE_CLASS_DIR" -name '*.class')

DEX_SRC="$BASE_DEX_DIR/classes.dex"
DEX_DST="$ROOT/src/jvmMain/resources/agent/ncprobe.dex"
cp -f "$DEX_SRC" "$DEX_DST"
WIFI_DEX_DST="$ROOT/src/jvmMain/resources/agent/wifi_tool.dex"
cp -f "$DEX_SRC" "$WIFI_DEX_DST"

echo "[rebuild] 编译输入 agent"
find "$ROOT/agent-src/com/newchar/probe/input" -name '*.java' -print0 | xargs -0 \
    "$JAVAC_BIN" -nowarn --release 8 -cp "$ANDROID_JAR" -d "$INPUT_CLASS_DIR"
"$D8_BIN" --min-api 27 --output "$INPUT_DEX_DIR" $(find "$INPUT_CLASS_DIR" -name '*.class')

INPUT_DEX_DST="$ROOT/src/jvmMain/resources/agent/input_tool.dex"
cp -f "$INPUT_DEX_DIR/classes.dex" "$INPUT_DEX_DST"
echo "[rebuild] 完成: ${DEX_DST}、${WIFI_DEX_DST}、${INPUT_DEX_DST}"