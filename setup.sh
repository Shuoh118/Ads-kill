#!/usr/bin/env bash
#
# 编译环境安装脚本。只下载到指定目录，不污染系统，不需要 root。
#
# 用法：
#   ./setup.sh                     # 默认装到 ~/android-build
#   ./setup.sh /opt/android-build  # 装到指定目录
#
# 装完会输出 export 语句，复制到终端或写进 ~/.bashrc 即可。
#
set -euo pipefail

# ---------------- 目标目录 ----------------
DEST="${1:-$HOME/android-build}"
DEST="$(cd / 2>/dev/null; echo "$DEST")"

BT_VER="35.0.0"
PLATFORM="android-35"
GRADLE_VER="8.9"

# 镜像（国内快）。想用官方源就把 MIRROR 换成官方地址
MIRROR_SDK="https://dl.google.com/android/repository"
MIRROR_GRADLE="https://mirrors.cloud.tencent.com/gradle"

echo "========================================"
echo "安装目录: $DEST"
echo "build-tools: $BT_VER"
echo "platform: $PLATFORM"
echo "gradle: $GRADLE_VER"
echo "========================================"

mkdir -p "$DEST"

# ---------------- 工具检查 ----------------
need() {
    command -v "$1" >/dev/null 2>&1 || {
        echo "缺少命令: $1"
        echo "  Ubuntu/Debian: sudo apt install $2"
        echo "  macOS:         brew install $3"
        exit 1
    }
}
need curl "curl unzip" "curl"
need unzip "unzip" "unzip"

# ---------------- 1. JDK ----------------
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    echo ">> JDK 已存在: $JAVA_HOME"
else
    echo ">> 检查系统 JDK"
    if command -v javac >/dev/null 2>&1; then
        JV=$(javac -version 2>&1 | awk '{print $2}' | cut -d. -f1)
        if [ "$JV" -ge 17 ] 2>/dev/null; then
            echo ">> 系统 JDK $JV 可用"
        else
            echo "!! 系统 JDK 版本过低（需要 17+），请先安装 JDK 17"
            echo "   Ubuntu: sudo apt install openjdk-17-jdk"
            echo "   macOS:  brew install --cask temurin@17"
            exit 1
        fi
    else
        echo "!! 没有找到 javac，请先安装 JDK 17"
        echo "   Ubuntu: sudo apt install openjdk-17-jdk"
        echo "   macOS:  brew install --cask temurin@17"
        echo "   Windows: https://adoptium.net/temurin/releases/?version=17"
        exit 1
    fi
fi

# ---------------- 2. Android SDK ----------------
SDK="$DEST/android-sdk"
if [ -d "$SDK/platforms/$PLATFORM" ] && [ -d "$SDK/build-tools/$BT_VER" ]; then
    echo ">> Android SDK 已存在，跳过"
else
    echo ">> 下载 Android 命令行工具"
    CT_ZIP="$DEST/cmdline-tools.zip"
    case "$(uname -s)-$(uname -m)" in
        Linux-x86_64)   CT="commandlinetools-linux-11076708_latest.zip" ;;
        Linux-aarch64)  CT="commandlinetools-linux-11076708_latest.zip" ;;
        Darwin-*)       CT="commandlinetools-mac-11076708_latest.zip" ;;
        *) echo "!! 未知平台: $(uname -s)-$(uname -m)"; exit 1 ;;
    esac

    curl -L --progress-bar -o "$CT_ZIP" "$MIRROR_SDK/$CT"
    rm -rf "$SDK/cmdline-tools"
    mkdir -p "$SDK/cmdline-tools"
    unzip -q "$CT_ZIP" -d "$SDK/cmdline-tools"
    mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest" 2>/dev/null || true
    rm -f "$CT_ZIP"

    echo ">> 接受许可并安装 platform / build-tools"
    yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" \
        --sdk_root="$SDK" \
        "platforms;$PLATFORM" "build-tools;$BT_VER" >/dev/null 2>&1 || {
            echo "!! sdkmanager 安装失败，可能是网络问题"
            echo "   手动执行:"
            echo "   $SDK/cmdline-tools/latest/bin/sdkmanager --sdk_root=$SDK 'platforms;$PLATFORM' 'build-tools;$BT_VER'"
            exit 1
        }
    echo ">> Android SDK 完成"
fi

# ---------------- 3. Gradle ----------------
GRADLE_HOME="$DEST/gradle-$GRADLE_VER"
if [ -x "$GRADLE_HOME/bin/gradle" ]; then
    echo ">> Gradle 已存在，跳过"
else
    echo ">> 下载 Gradle $GRADLE_VER"
    G_ZIP="$DEST/gradle.zip"
    curl -L --progress-bar -o "$G_ZIP" "$MIRROR_GRADLE/gradle-$GRADLE_VER-bin.zip"
    unzip -q "$G_ZIP" -d "$DEST"
    rm -f "$G_ZIP"
    echo ">> Gradle 完成"
fi

# ---------------- 4. 输出 ----------------
cat <<EOF

========================================
安装完成
========================================
把下面几行加到 ~/.bashrc 或 ~/.zshrc：

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export GRADLE_HOME="$GRADLE_HOME"
export PATH="\$PATH:$SDK/platforms/$PLATFORM/tools:$SDK/build-tools/$BT_VER:\$GRADLE_HOME/bin"

然后新开终端，或直接执行：

export ANDROID_HOME="$SDK"
export GRADLE_HOME="$GRADLE_HOME"
export PATH="\$PATH:\$GRADLE_HOME/bin"

验证：
  aapt2 version
  sdkmanager --version
  gradle --version

构建本项目：
  cd $(dirname "$0")
  ./build.sh          # 无需 Gradle，最快
  ./gradlew assembleRelease   # 用 Gradle

注意：build.sh 和 gradlew 都要能找到 ANDROID_HOME。
========================================
EOF
