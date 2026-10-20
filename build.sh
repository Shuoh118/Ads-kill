#!/usr/bin/env bash
#
# 无 Gradle 构建脚本。只需要 JDK 17 + Android SDK（build-tools + platform）。
#
# 用法：
#   ./build.sh                # 出 debug + release 两个包
#   ./build.sh debug          # 只出 debug
#   ./build.sh release        # 只出 release
#
# 环境变量：
#   ANDROID_HOME   SDK 路径，默认 /opt/android
#   KEYSTORE       密钥路径，默认 ../release.keystore.p12
#   KS_ALIAS       密钥别名，默认 shuo
#   KS_PASS        库密码，默认 shuo
#   KEY_PASS       密钥密码，默认 shuo
#
# release 找不到密钥时，退回 debug 签名，不会中断。
set -euo pipefail
cd "$(dirname "$0")"

# ---------------- 配置 ----------------
PROJECT="adskill"
VERSION="2.0"
VERSION_CODE="2"

ANDROID_HOME="${ANDROID_HOME:-/opt/android}"
BT_VERSION="${BT_VERSION:-35.0.0}"
PLATFORM="${PLATFORM:-android-35}"
MIN_SDK="${MIN_SDK:-26}"
TARGET_SDK="${TARGET_SDK:-35}"

KEYSTORE="${KEYSTORE:-../release.keystore.p12}"
KS_ALIAS="${KS_ALIAS:-shuo}"
KS_PASS="${KS_PASS:-shuo}"
KEY_PASS="${KEY_PASS:-shuo}"

MODE="${1:-both}"

BT="$ANDROID_HOME/build-tools/$BT_VERSION"
AJ="$ANDROID_HOME/platforms/$PLATFORM/android.jar"
OUT="dist"
WORK=".build"

# ---------------- 检查 ----------------
die() { echo "错误: $*" >&2; exit 1; }

[ -d "$BT" ] || die "找不到 build-tools: $BT
  装：sdkmanager 'build-tools;$BT_VERSION'"
[ -f "$AJ" ] || die "找不到 platform: $AJ
  装：sdkmanager 'platforms;$PLATFORM'"

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAVA="$JAVA_HOME/bin/java"
    KEYTOOL="$JAVA_HOME/bin/keytool"
else
    JAVAC="$(command -v javac || true)"
    JAVA="$(command -v java || true)"
    KEYTOOL="$(command -v keytool || true)"
    [ -n "$JAVAC" ] || die "找不到 javac，装 JDK 17"
fi

# arm64 跑 x86_64 build-tools 需要 qemu
RUN=""
if ! "$BT/aapt2" version >/dev/null 2>&1; then
    if command -v qemu-x86_64-static >/dev/null 2>&1; then
        RUN="qemu-x86_64-static"
        echo ">> build-tools 需要 qemu 转译"
    fi
fi

# libxposed api
API_JAR=""
for c in "app/libs/api-102.0.0.aar" "lib/api-102.0.0.aar"; do
    [ -f "$c" ] && API_JAR="$c" && break
done
[ -n "$API_JAR" ] || die "找不到 libxposed api，把 api-102.0.0.aar 放到 app/libs/"

# ---------------- 编译 ----------------
echo ">> 清理"
rm -rf "$WORK"
mkdir -p "$WORK"/{gen,cls,dex,res} "$OUT"

if [[ "$API_JAR" == *.aar ]]; then
    unzip -o -q "$API_JAR" classes.jar -d "$WORK/aar"
    cp "$WORK/aar/classes.jar" "$WORK/api.jar"
else
    cp "$API_JAR" "$WORK/api.jar"
fi

echo ">> AndroidManifest"
sed 's|xmlns:android="http://schemas.android.com/apk/res/android"|xmlns:android="http://schemas.android.com/apk/res/android" package="io.github.shuoh118.adskill"|' \
    app/src/main/AndroidManifest.xml > "$WORK/AndroidManifest.xml"

echo ">> aapt2 link"
$RUN "$BT/aapt2" link \
    -o "$WORK/base.apk" \
    --manifest "$WORK/AndroidManifest.xml" \
    -I "$AJ" \
    --java "$WORK/gen" \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION"

echo ">> javac"
find app/src/main/java "$WORK/gen" -name "*.java" > "$WORK/srcs.txt"
"$JAVAC" -source 17 -target 17 -encoding UTF-8 \
    -classpath "$AJ:$WORK/api.jar" \
    -d "$WORK/cls" \
    @"$WORK/srcs.txt"

echo ">> d8"
"$JAVA" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
    --min-api "$MIN_SDK" \
    --output "$WORK/dex" \
    $(find "$WORK/cls" -name "*.class")

echo ">> 打包"
cp "$WORK/dex/classes.dex" "$WORK/"
cp -r app/src/main/resources/META-INF "$WORK/res/"
(
    cd "$WORK"
    zip -q -j base.apk classes.dex
    cd res
    zip -q -r "../base.apk" META-INF
)

echo ">> zipalign"
$RUN "$BT/zipalign" -f -p 4 "$WORK/base.apk" "$WORK/aligned.apk"

# ---------------- 签名 ----------------
# debug 密钥：Android 官方通用 debug keystore，与 Android Studio 一致
DEBUG_KS="$HOME/.android/debug.keystore"
DEBUG_ALIAS="androiddebugkey"
DEBUG_PASS="android"

ensure_debug_ks() {
    if [ -f "$DEBUG_KS" ]; then
        return 0
    fi
    [ -n "$KEYTOOL" ] || return 1
    mkdir -p "$(dirname "$DEBUG_KS")"
    "$KEYTOOL" -genkeypair -v \
        -keystore "$DEBUG_KS" \
        -storepass "$DEBUG_PASS" \
        -keypass "$DEBUG_PASS" \
        -alias "$DEBUG_ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
}

sign_with() {
    local ks="$1" alias="$2" pass="$3" out="$4" label="$5"
    "$JAVA" -jar "$BT/lib/apksigner.jar" sign \
        --ks "$ks" \
        --ks-type PKCS12 \
        --ks-key-alias "$alias" \
        --ks-pass "pass:$pass" \
        --key-pass "pass:$pass" \
        --min-sdk-version "$MIN_SDK" \
        --v1-signing-enabled false \
        --v2-signing-enabled true \
        --v3-signing-enabled true \
        --out "$out" \
        "$WORK/aligned.apk" || return 1
    echo "   签名: $label"
    return 0
}

build_debug() {
    echo ">> 构建 debug 签名版"
    if ! ensure_debug_ks; then
        echo "   !! 无法生成 debug 密钥，跳过"
        return 1
    fi
    local out="$OUT/$PROJECT-$VERSION-debug.apk"
    "$JAVA" -jar "$BT/lib/apksigner.jar" sign \
        --ks "$DEBUG_KS" \
        --ks-type JKS \
        --ks-key-alias "$DEBUG_ALIAS" \
        --ks-pass "pass:$DEBUG_PASS" \
        --key-pass "pass:$DEBUG_PASS" \
        --min-sdk-version "$MIN_SDK" \
        --v1-signing-enabled false \
        --v2-signing-enabled true \
        --v3-signing-enabled true \
        --out "$out" \
        "$WORK/aligned.apk" || return 1
    echo "   -> $out"
}

build_release() {
    echo ">> 构建 release 签名版"
    local out="$OUT/$PROJECT-$VERSION-release.apk"
    if [ -f "$KEYSTORE" ]; then
        sign_with "$KEYSTORE" "$KS_ALIAS" "$KS_PASS" "$out" "release (你的密钥)" || return 1
        echo "   验证:"
        "$JAVA" -jar "$BT/lib/apksigner.jar" verify -v --min-sdk-version "$MIN_SDK" "$out" 2>&1 | sed 's/^/     /'
    else
        echo "   !! 找不到 $KEYSTORE，改用 debug 签名"
        if ! ensure_debug_ks; then
            echo "   !! 也无法生成 debug 密钥，跳过"
            return 1
        fi
        "$JAVA" -jar "$BT/lib/apksigner.jar" sign \
            --ks "$DEBUG_KS" \
            --ks-type JKS \
            --ks-key-alias "$DEBUG_ALIAS" \
            --ks-pass "pass:$DEBUG_PASS" \
            --key-pass "pass:$DEBUG_PASS" \
            --min-sdk-version "$MIN_SDK" \
            --out "$out" \
            "$WORK/aligned.apk" || return 1
    fi
    echo "   -> $out"
}

case "$MODE" in
    debug)   build_debug ;;
    release) build_release ;;
    both)    build_debug; build_release ;;
    *)       die "未知模式: $MODE（可选 debug / release / both）" ;;
esac

echo
echo "================================================"
echo "构建完成"
ls -lh "$OUT"/*.apk 2>/dev/null || true
echo "================================================"
