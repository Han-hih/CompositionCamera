#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<USAGE
Usage:
  scripts/new-module.sh <module_path> [options]

Arguments:
  <module_path>            Module path like: feature/camera/domain

Options:
  --type <library|application>   Android module type (default: library)
  --namespace <value>            Full namespace override
  --compose                      Add compose plugin + buildFeatures.compose
  --hilt                         Add hilt plugin + runtime dependency
  --ksp                          Add ksp plugin + ksp dependency (for hilt)
  --compile-sdk <int>            compileSdk (default: 36)
  --min-sdk <int>                minSdk (default: 30)
  -h, --help                     Show this help

Examples:
  scripts/new-module.sh feature/camera/domain --type library
  scripts/new-module.sh feature/camera/presentation --compose --hilt --ksp
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" || $# -eq 0 ]]; then
  usage
  exit 0
fi

MODULE_PATH="$1"
shift

TYPE="library"
COMPILE_SDK="36"
MIN_SDK="30"
ENABLE_COMPOSE="false"
ENABLE_HILT="false"
ENABLE_KSP="false"
NAMESPACE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --type)
      TYPE="${2:-}"
      shift 2
      ;;
    --namespace)
      NAMESPACE="${2:-}"
      shift 2
      ;;
    --compose)
      ENABLE_COMPOSE="true"
      shift
      ;;
    --hilt)
      ENABLE_HILT="true"
      shift
      ;;
    --ksp)
      ENABLE_KSP="true"
      shift
      ;;
    --compile-sdk)
      COMPILE_SDK="${2:-}"
      shift 2
      ;;
    --min-sdk)
      MIN_SDK="${2:-}"
      shift 2
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ "$TYPE" != "library" && "$TYPE" != "application" ]]; then
  echo "--type must be 'library' or 'application'" >&2
  exit 1
fi

if [[ "$ENABLE_HILT" == "true" && "$ENABLE_KSP" != "true" ]]; then
  echo "Hint: when using --hilt, add --ksp as well for annotation processing." >&2
fi

ROOT_DIR="$(pwd)"
MODULE_DIR="$ROOT_DIR/$MODULE_PATH"
SETTINGS_FILE="$ROOT_DIR/settings.gradle.kts"

if [[ ! -f "$SETTINGS_FILE" ]]; then
  echo "settings.gradle.kts not found in current directory" >&2
  exit 1
fi

# Derive namespace from app namespace + module path if not provided.
if [[ -z "$NAMESPACE" ]]; then
  APP_NAMESPACE="$(rg -no 'namespace\s*=\s*"([^"]+)"' app/build.gradle.kts -r '$1' | head -n1 || true)"
  if [[ -z "$APP_NAMESPACE" ]]; then
    APP_NAMESPACE="com.app"
  fi
  SUFFIX="$(echo "$MODULE_PATH" | tr '/' '.')"
  NAMESPACE="$APP_NAMESPACE.$SUFFIX"
fi

JAVA_PKG_PATH="$(echo "$NAMESPACE" | tr '.' '/')"

mkdir -p "$MODULE_DIR/src/main/java/$JAVA_PKG_PATH"
mkdir -p "$MODULE_DIR/src/main"

PLUGIN_BLOCK="plugins {\n"
if [[ "$TYPE" == "application" ]]; then
  PLUGIN_BLOCK+="    alias(libs.plugins.android.application)\n"
else
  PLUGIN_BLOCK+="    alias(libs.plugins.android.library)\n"
fi
if [[ "$ENABLE_COMPOSE" == "true" ]]; then
  PLUGIN_BLOCK+="    alias(libs.plugins.kotlin.compose)\n"
fi
if [[ "$ENABLE_KSP" == "true" ]]; then
  PLUGIN_BLOCK+="    alias(libs.plugins.ksp)\n"
fi
if [[ "$ENABLE_HILT" == "true" ]]; then
  PLUGIN_BLOCK+="    alias(libs.plugins.hilt.android)\n"
fi
PLUGIN_BLOCK+="}\n"

ANDROID_BLOCK="android {\n"
ANDROID_BLOCK+="    namespace = \"$NAMESPACE\"\n"
ANDROID_BLOCK+="    compileSdk = $COMPILE_SDK\n\n"
ANDROID_BLOCK+="    defaultConfig {\n"
if [[ "$TYPE" == "application" ]]; then
  APP_ID="$NAMESPACE"
  ANDROID_BLOCK+="        applicationId = \"$APP_ID\"\n"
fi
ANDROID_BLOCK+="        minSdk = $MIN_SDK\n"
ANDROID_BLOCK+="    }\n\n"
ANDROID_BLOCK+="    compileOptions {\n"
ANDROID_BLOCK+="        sourceCompatibility = JavaVersion.VERSION_17\n"
ANDROID_BLOCK+="        targetCompatibility = JavaVersion.VERSION_17\n"
ANDROID_BLOCK+="    }\n"
if [[ "$ENABLE_COMPOSE" == "true" ]]; then
  ANDROID_BLOCK+="\n    buildFeatures {\n"
  ANDROID_BLOCK+="        compose = true\n"
  ANDROID_BLOCK+="    }\n"
fi
ANDROID_BLOCK+="}\n"

DEPENDENCIES_BLOCK="dependencies {\n"
if [[ "$ENABLE_HILT" == "true" ]]; then
  DEPENDENCIES_BLOCK+="    implementation(libs.hilt.android)\n"
  if [[ "$ENABLE_KSP" == "true" ]]; then
    DEPENDENCIES_BLOCK+="    ksp(libs.hilt.compiler)\n"
  fi
fi
if [[ "$ENABLE_COMPOSE" == "true" ]]; then
  DEPENDENCIES_BLOCK+="    implementation(platform(libs.androidx.compose.bom))\n"
  DEPENDENCIES_BLOCK+="    implementation(libs.androidx.compose.ui)\n"
  DEPENDENCIES_BLOCK+="    implementation(libs.androidx.compose.ui.graphics)\n"
  DEPENDENCIES_BLOCK+="    implementation(libs.androidx.compose.ui.tooling.preview)\n"
  DEPENDENCIES_BLOCK+="    implementation(libs.androidx.compose.material3)\n"
fi
DEPENDENCIES_BLOCK+="}\n"

printf "%b\n%b\n%b" "$PLUGIN_BLOCK" "$ANDROID_BLOCK" "$DEPENDENCIES_BLOCK" > "$MODULE_DIR/build.gradle.kts"
printf "<manifest package=\"%s\" />\n" "$NAMESPACE" > "$MODULE_DIR/src/main/AndroidManifest.xml"

INCLUDE_PATH=":$(echo "$MODULE_PATH" | tr '/' ':')"
INCLUDE_LINE="include(\"$INCLUDE_PATH\")"
if ! rg -q "^include\(\"$INCLUDE_PATH\"\)" "$SETTINGS_FILE"; then
  printf "%s\n" "$INCLUDE_LINE" >> "$SETTINGS_FILE"
fi

echo "Created module: $INCLUDE_PATH"
echo "Namespace: $NAMESPACE"
echo "Path: $MODULE_DIR"
