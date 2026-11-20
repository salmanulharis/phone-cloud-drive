#!/usr/bin/env bash
# Builds everything the app bundles for its built-in server:
#
#   app/src/main/jniLibs/<abi>/libsftpgo.so        SFTPGo (web drive, WebDAV, admin API)
#   app/src/main/jniLibs/<abi>/libcloudflared.so   Cloudflare Tunnel client
#   app/src/main/assets/sftpgo-ui.zip              SFTPGo's web templates + this project's patches/theme
#
# Usage (Linux, macOS or Git Bash on Windows; needs Go, git, patch, a JDK and the Android NDK):
#   bash android/native/build.sh             # arm64-v8a (almost every phone)
#   bash android/native/build.sh all         # arm64-v8a armeabi-v7a x86_64
#   bash android/native/build.sh x86_64      # e.g. for the emulator
#
# The daemons are executables named lib*.so: Android only lets apps run programs from
# nativeLibraryDir, which is where the installer extracts jniLibs. They are built with cgo
# against the NDK so DNS goes through bionic's resolver (Android has no /etc/resolv.conf)
# and SQLite is compiled in.
set -euo pipefail

SFTPGO_VERSION="${SFTPGO_VERSION:-v2.7.6}"
CLOUDFLARED_VERSION="${CLOUDFLARED_VERSION:-2026.11.1}"
API=29
# SFTPGo features the app doesn't use: cloud storage backends, other databases, metrics.
SFTPGO_TAGS="nos3 nogcs noazblob nomysql nopgsql nobolt nometrics"

here="$(cd "$(dirname "$0")" && pwd)"
android="$(dirname "$here")"
repo="$(dirname "$android")"
work="$here/.work"
jni="$android/app/src/main/jniLibs"
assets="$android/app/src/main/assets"

say() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }
die() { echo "build.sh: $*" >&2; exit 1; }

case "${1:-}" in
  "") abis=(arm64-v8a) ;;
  all) abis=(arm64-v8a armeabi-v7a x86_64) ;;
  *) abis=("$@") ;;
esac

command -v go >/dev/null || die "Go is not installed (https://go.dev/dl)"
command -v git >/dev/null || die "git is not installed"
command -v patch >/dev/null || die "patch is not installed"
command -v jar >/dev/null || die "a JDK (jar) is needed to package the web UI"

# ---- Android NDK -------------------------------------------------------------
ndk="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$ndk" ]]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${LOCALAPPDATA:-$HOME}/Android/Sdk}}"
  [[ -d "$sdk" ]] || sdk="$HOME/Android/Sdk"
  ndk="$(ls -d "$sdk"/ndk/* 2>/dev/null | sort -V | tail -1 || true)"
fi
[[ -d "$ndk" ]] || die "Android NDK not found; set ANDROID_NDK_HOME"
case "$(uname -s)" in
  Linux*) host=linux-x86_64; exe="" ;;
  Darwin*) host=darwin-x86_64; exe="" ;;
  MINGW*|MSYS*|CYGWIN*) host=windows-x86_64; exe=".exe" ;;
  *) die "unsupported build host $(uname -s)" ;;
esac
clang="$ndk/toolchains/llvm/prebuilt/$host/bin/clang$exe"
[[ -x "$clang" ]] || die "clang not found at $clang"
# Go splits CC on spaces, so hand it a native (C:/...) path on Windows.
if [[ $host == windows-x86_64 ]]; then clang="$(cygpath -m "$clang")"; fi

gobuild() { # abi srcdir package output-name tags ldflags
  local abi=$1 src=$2 pkg=$3 name=$4 tags=$5 ldflags=$6 goarch goarm="" target
  case $abi in
    arm64-v8a)   goarch=arm64; target=aarch64-linux-android$API ;;
    armeabi-v7a) goarch=arm; goarm=7; target=armv7a-linux-androideabi$API ;;
    x86_64)      goarch=amd64; target=x86_64-linux-android$API ;;
    *) die "unsupported ABI: $abi" ;;
  esac
  mkdir -p "$jni/$abi"
  echo "  $name ($abi)"
  (cd "$src" && GOOS=android GOARCH=$goarch GOARM=$goarm CGO_ENABLED=1 CC="$clang --target=$target" \
    go build -trimpath -buildvcs=false -tags "$tags" -ldflags "-s -w $ldflags" -o "$jni/$abi/lib$name.so" "$pkg")
}

fetch() { # url tag dir
  [[ -d "$work/$3" ]] || git -c advice.detachedHead=false clone --quiet --depth 1 --branch "$2" "$1" "$work/$3"
}

mkdir -p "$work"
say "Sources"
fetch https://github.com/drakkan/sftpgo.git "$SFTPGO_VERSION" "sftpgo-$SFTPGO_VERSION"
fetch https://github.com/cloudflare/cloudflared.git "$CLOUDFLARED_VERSION" "cloudflared-$CLOUDFLARED_VERSION"
sftpgo_src="$work/sftpgo-$SFTPGO_VERSION"
cloudflared_src="$work/cloudflared-$CLOUDFLARED_VERSION"
sftpgo_commit="$(git -C "$sftpgo_src" rev-parse --short HEAD)"

say "Binaries"
for abi in "${abis[@]}"; do
  gobuild "$abi" "$sftpgo_src" . sftpgo "$SFTPGO_TAGS" \
    "-X github.com/drakkan/sftpgo/v2/internal/version.commit=$sftpgo_commit"
  gobuild "$abi" "$cloudflared_src" ./cmd/cloudflared cloudflared "" \
    "-X main.Version=$CLOUDFLARED_VERSION"
done

# ---- Web UI: SFTPGo's templates/static + patches + theme ---------------------
say "Web UI"
ui="$work/ui"
rm -rf "$ui"
mkdir -p "$ui"
cp -r "$sftpgo_src/templates" "$ui/templates"
cp -r "$sftpgo_src/static" "$ui/static"
for p in "$repo"/server/sftpgo/patches/*.patch; do
  patch -s -p1 --dry-run -d "$ui/templates" < "$p" >/dev/null \
    || die "$(basename "$p") does not apply to SFTPGo $SFTPGO_VERSION"
  patch -s -p1 -d "$ui/templates" < "$p"
  echo "  applied $(basename "$p")"
done
mkdir -p "$ui/static/drive"
cp "$repo"/server/sftpgo/static/drive/* "$ui/static/drive/"
# The app re-extracts the UI whenever this stamp changes (new SFTPGo, patch or theme).
stamp="$(cat "$repo"/server/sftpgo/patches/*.patch "$repo"/server/sftpgo/static/drive/* | git hash-object --stdin)"
echo "$SFTPGO_VERSION-${stamp:0:12}" > "$ui/VERSION"
mkdir -p "$assets"
rm -f "$assets/sftpgo-ui.zip"
jar --create --no-manifest --file "$assets/sftpgo-ui.zip" -C "$ui" .

say "Done"
ls -l "$assets/sftpgo-ui.zip"
for abi in "${abis[@]}"; do ls -l "$jni/$abi"; done
