#!/bin/sh

set -eu

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILDDEPS_DIR="$ROOT_DIR/.builddeps"
EXTRACTED_DIR="$BUILDDEPS_DIR/extracted"
APP_BUILD_DIR="$ROOT_DIR/app/build/rutophone"
OUT_DIR="$ROOT_DIR/out"
GENERATED_SRC_DIR="$APP_BUILD_DIR/generated-src"
CLASSES_DIR="$APP_BUILD_DIR/classes"
JARS_DIR="$APP_BUILD_DIR/jars"
APP_PROCESS_DIR="$APP_BUILD_DIR/app_process"
TEMPLATES_DIR="$ROOT_DIR/templates"

ANDROID_JAR_URL="https://repo.maven.apache.org/maven2/com/google/android/android/4.1.1.4/android-4.1.1.4.jar"
ANDROID_APP_PROCESS_AAR_URL="https://repo.maven.apache.org/maven2/io/github/iamr0s/AndroidAppProcess/1.4.1/AndroidAppProcess-1.4.1.aar"
R8_JAR_URL="https://storage.googleapis.com/r8-releases/raw/8.10.21/r8.jar"

ANDROID_JAR_PATH="$BUILDDEPS_DIR/android.jar"
ANDROID_APP_PROCESS_AAR_PATH="$BUILDDEPS_DIR/AndroidAppProcess.aar"
R8_JAR_PATH="$BUILDDEPS_DIR/r8.jar"
EXTRACTED_CLASSES_JAR_PATH="$EXTRACTED_DIR/classes.jar"
VERSION_FILE_PATH="$ROOT_DIR/VERSION"
BUILD_CONFIG_TEMPLATE_PATH="$TEMPLATES_DIR/BuildConfig.java.template"
GENERATED_BUILD_CONFIG_PATH="$GENERATED_SRC_DIR/dex/rutophone/BuildConfig.java"
MAIN_JAR_PATH="$JARS_DIR/rutophone-classes.jar"
DEX_PATH="$OUT_DIR/rutophone.dex"
APP_PROCESS_JAR_PATH="$OUT_DIR/rutophone.jar"

download_if_missing() {
    url="$1"
    output="$2"

    if [ -f "$output" ]; then
        return
    fi

    mkdir -p "$(dirname "$output")"
    curl -fL "$url" -o "$output"
}

resolve_base_version() {
    if [ -f "$VERSION_FILE_PATH" ]; then
        tr -d '\r' < "$VERSION_FILE_PATH" | head -n 1
        return
    fi
    echo "0.1.0"
}

resolve_git_short_id() {
    if git -C "$ROOT_DIR" rev-parse --short HEAD >/dev/null 2>&1; then
        git -C "$ROOT_DIR" rev-parse --short HEAD
        return
    fi
    echo "nogit"
}

generate_build_config() {
    base_version="$1"
    git_short_id="$2"
    version="$3"

    mkdir -p "$(dirname "$GENERATED_BUILD_CONFIG_PATH")"
    sed \
        -e "s/__BASE_VERSION__/${base_version}/g" \
        -e "s/__GIT_SHORT_ID__/${git_short_id}/g" \
        -e "s/__VERSION__/${version}/g" \
        "$BUILD_CONFIG_TEMPLATE_PATH" > "$GENERATED_BUILD_CONFIG_PATH"
}

main() {
    base_version="$(resolve_base_version)"
    git_short_id="$(resolve_git_short_id)"
    if [ "$git_short_id" = "nogit" ]; then
        version="$base_version"
    else
        version="$base_version-r$git_short_id"
    fi

    rm -rf "$APP_BUILD_DIR" "$OUT_DIR"
    mkdir -p "$BUILDDEPS_DIR" "$EXTRACTED_DIR" "$CLASSES_DIR" "$JARS_DIR" "$APP_PROCESS_DIR" "$OUT_DIR"

    download_if_missing "$ANDROID_JAR_URL" "$ANDROID_JAR_PATH"
    download_if_missing "$ANDROID_APP_PROCESS_AAR_URL" "$ANDROID_APP_PROCESS_AAR_PATH"
    download_if_missing "$R8_JAR_URL" "$R8_JAR_PATH"

    unzip -o "$ANDROID_APP_PROCESS_AAR_PATH" classes.jar -d "$EXTRACTED_DIR" >/dev/null
    generate_build_config "$base_version" "$git_short_id" "$version"
    javac \
        --release 8 \
        -cp "$ANDROID_JAR_PATH:$EXTRACTED_CLASSES_JAR_PATH" \
        -d "$CLASSES_DIR" \
        "$ROOT_DIR/app/src/main/java/dex/rutophone/Main.java" \
        "$GENERATED_BUILD_CONFIG_PATH"

    (
        cd "$CLASSES_DIR"
        jar cf "$MAIN_JAR_PATH" .
    )

    java -cp "$R8_JAR_PATH" com.android.tools.r8.D8 \
        --lib "$ANDROID_JAR_PATH" \
        --output "$APP_BUILD_DIR" \
        "$MAIN_JAR_PATH" \
        "$EXTRACTED_CLASSES_JAR_PATH"

    mv -f "$APP_BUILD_DIR/classes.dex" "$DEX_PATH"
    cp "$DEX_PATH" "$APP_PROCESS_DIR/classes.dex"
    (
        cd "$APP_PROCESS_DIR"
        zip -q -j "$APP_PROCESS_JAR_PATH" classes.dex
    )

    printf 'dex: %s\n' "$DEX_PATH"
    printf 'app_process jar: %s\n' "$APP_PROCESS_JAR_PATH"
    printf 'version: %s\n' "$version"
}

main "$@"
