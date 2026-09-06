#!/usr/bin/env bash
# Gradle/AGP 없이 APK 를 만든다.
#
# Android Studio 가 있으면 그냥 Gradle 로 빌드하면 된다. 이 스크립트는 Google Maven 에
# 닿을 수 없는 환경(CI 샌드박스 등)에서 컴파일과 패키징을 검증하기 위한 것이다.
# AndroidX 를 쓰지 않으므로 android.jar + kotlinc + d8 + aapt2 만 있으면 된다.
#
#   ./build.sh apk    # app/build/kashi-debug.apk
#   ./build.sh test   # JVM 단위 테스트 (app/src/test)
#
# 도구 위치는 환경변수로 준다. 기본값은 이 저장소를 만든 샌드박스 경로다.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP="$HERE/app"
OUT="$APP/build"

SDK_ROOT="${SDK_ROOT:-/tmp/claude-0/-home-user--/c902898b-2318-5478-9fcf-8239dec7e91e/scratchpad/sdk}"
ANDROID_JAR="${ANDROID_JAR:-$SDK_ROOT/platforms/android-35/android.jar}"
# Debian 의 aapt2 (14~beta1) 는 API 35 의 resources.arsc 형식을 읽지 못한다. 리소스 링크에만
# 한 단계 낮은 플랫폼을 쓴다. 우리가 참조하는 프레임워크 리소스는 전부 그 이전부터 있던 것들이다.
LINK_ANDROID_JAR="${LINK_ANDROID_JAR:-$SDK_ROOT/platforms/android-34/android.jar}"
KOTLINC="${KOTLINC:-$SDK_ROOT/kotlinc/bin/kotlinc}"
R8LIB="${R8LIB:-$SDK_ROOT/r8/r8lib.jar}"
LIBS_DIR="${LIBS_DIR:-$SDK_ROOT/libs}"
TESTLIBS_DIR="${TESTLIBS_DIR:-$SDK_ROOT/testlibs}"
AAPT2="${AAPT2:-aapt2}"
ZIPALIGN="${ZIPALIGN:-zipalign}"
APKSIGNER="${APKSIGNER:-apksigner}"

MIN_SDK=26
PACKAGE=com.kashi.lyrics

# JAVA_TOOL_OPTIONS 의 프록시 설정이 매 실행마다 stderr 에 찍히는 것을 막는다.
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-}"

log() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }

require() {
  for path in "$@"; do
    [[ -e "$path" ]] || { echo "없음: $path" >&2; exit 1; }
  done
}

runtime_classpath() {
  local cp=""
  for jar in "$LIBS_DIR"/*.jar; do cp="${cp:+$cp:}$jar"; done
  echo "$cp"
}

compile_sources() {
  # $1 = 출력 디렉터리, $2.. = 소스 루트
  local out="$1"; shift
  local cp="$ANDROID_JAR:$(runtime_classpath)"
  rm -rf "$out"; mkdir -p "$out"

  local sources=()
  for root in "$@"; do
    while IFS= read -r f; do sources+=("$f"); done < <(find "$root" -name '*.kt' -o -name '*.java' | sort)
  done

  # kotlinc 는 .java 를 참조 해석에만 쓰고 컴파일하지는 않는다. R.java 는 javac 로 따로 돈다.
  "$KOTLINC" -no-stdlib -no-reflect \
    -jvm-target 17 \
    -cp "$cp" \
    -d "$out" \
    "${sources[@]}" 2>&1 | grep -v '^warning: .*JAVA_TOOL_OPTIONS' || true

  local javas=()
  for f in "${sources[@]}"; do [[ "$f" == *.java ]] && javas+=("$f"); done
  if (( ${#javas[@]} )); then
    javac -source 17 -target 17 -nowarn \
      -cp "$cp:$out" -d "$out" "${javas[@]}" 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
  fi

  # 컴파일이 실제로 뭔가 냈는지 확인한다. 위에서 grep 이 종료코드를 삼키기 때문이다.
  find "$out" -name '*.class' | grep -q . || { echo "컴파일 실패: 클래스가 없습니다" >&2; exit 1; }
}

build_apk() {
  require "$ANDROID_JAR" "$LINK_ANDROID_JAR" "$KOTLINC" "$R8LIB" "$LIBS_DIR"
  command -v "$AAPT2" >/dev/null || { echo "aapt2 가 없습니다" >&2; exit 1; }

  rm -rf "$OUT"; mkdir -p "$OUT/res" "$OUT/gen" "$OUT/dex"

  log "리소스 컴파일"
  "$AAPT2" compile --dir "$APP/src/main/res" -o "$OUT/res/res.zip"

  log "리소스 링크 + R.java"
  # AGP 8 은 소스 매니페스트에 package 를 적지 못하게 하고 namespace 에서 주입한다. 같은 일을 여기서 한다.
  sed "s#<manifest #<manifest package=\"$PACKAGE\" #" "$APP/src/main/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
  "$AAPT2" link \
    -I "$LINK_ANDROID_JAR" \
    --manifest "$OUT/AndroidManifest.xml" \
    --java "$OUT/gen" \
    --min-sdk-version "$MIN_SDK" --target-sdk-version 35 \
    --version-code 1 --version-name 0.1 \
    -o "$OUT/resources.apk" \
    "$OUT/res/res.zip"

  log "Kotlin 컴파일"
  compile_sources "$OUT/classes" "$APP/src/main/java" "$OUT/gen"

  log "dex 변환 (d8)"
  # d8 은 클래스 디렉터리를 받지 않는다. jar 로 묶어 넘긴다.
  jar --create --file "$OUT/classes.jar" -C "$OUT/classes" .
  java -cp "$R8LIB" com.android.tools.r8.D8 \
    --release --min-api "$MIN_SDK" \
    --lib "$ANDROID_JAR" \
    --output "$OUT/dex" \
    "$OUT/classes.jar" "$LIBS_DIR"/*.jar 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
  require "$OUT/dex/classes.dex"

  log "패키징"
  cp "$OUT/resources.apk" "$OUT/unaligned.apk"
  (cd "$OUT/dex" && zip -q "$OUT/unaligned.apk" classes*.dex)
  "$ZIPALIGN" -f -p 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

  log "서명 (디버그 키)"
  local keystore="$OUT/debug.keystore"
  [[ -f "$keystore" ]] || keytool -genkeypair -v -keystore "$keystore" \
    -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
  "$APKSIGNER" sign --ks "$keystore" --ks-pass pass:android --key-pass pass:android \
    --out "$OUT/kashi-debug.apk" "$OUT/aligned.apk"
  "$APKSIGNER" verify --print-certs "$OUT/kashi-debug.apk" | head -3

  log "완료: $OUT/kashi-debug.apk"
  ls -la "$OUT/kashi-debug.apk"
}

run_tests() {
  require "$ANDROID_JAR" "$KOTLINC" "$TESTLIBS_DIR"
  local test_out="$OUT/test-classes"
  local main_out="$OUT/classes"

  # 단위 테스트는 앱 클래스가 있어야 하므로 먼저 본체를 컴파일한다.
  [[ -d "$main_out" ]] || build_apk >/dev/null

  log "테스트 컴파일"
  local cp="$ANDROID_JAR:$(runtime_classpath):$main_out"
  for jar in "$TESTLIBS_DIR"/*.jar; do cp="$cp:$jar"; done
  rm -rf "$test_out"; mkdir -p "$test_out"
  "$KOTLINC" -no-stdlib -no-reflect -jvm-target 17 -cp "$cp" -d "$test_out" \
    $(find "$APP/src/test" -name '*.kt') 2>&1 | grep -v JAVA_TOOL_OPTIONS || true

  log "테스트 실행"
  # android.jar 는 스텁이라 런타임에 쓸 수 없다. org.json 은 순수 JVM 구현으로 바꿔 넣고,
  # 프레임워크 클래스를 건드리지 않는 테스트만 여기서 돈다.
  local classes
  classes=$(cd "$test_out" && find . -name '*Test.class' | sed 's#^\./##; s#\.class$##; s#/#.#g' | tr '\n' ' ')
  # android.jar 는 클래스 로딩(검증)용으로만 얹는다. 실제 프레임워크 호출은 "Stub!" 을 던진다.
  java -cp "$test_out:$main_out:$(runtime_classpath):$(echo "$TESTLIBS_DIR"/*.jar | tr ' ' ':'):$ANDROID_JAR" \
    org.junit.runner.JUnitCore $classes 2>&1 | grep -v JAVA_TOOL_OPTIONS
}

case "${1:-apk}" in
  apk)  build_apk ;;
  test) run_tests ;;
  *) echo "사용법: $0 [apk|test]" >&2; exit 2 ;;
esac
