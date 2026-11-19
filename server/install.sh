#!/data/data/com.termux/files/usr/bin/bash
# One-time setup of the drive server inside Termux. Safe to re-run.
# Usage (inside Termux, from the repository root):  bash server/install.sh
set -eu

HERE="$(cd "$(dirname "$0")" && pwd)"
say() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }

if [ -z "${PREFIX:-}" ] || [ ! -d "$PREFIX" ]; then
  echo "Run this inside Termux." >&2
  exit 1
fi

say "Installing packages"
pkg update -y
pkg install -y openssh sftpgo cloudflared patch sqlite curl

say "Checking shared-storage access"
if [ ! -w /sdcard ]; then
  echo "Termux can't write to shared storage yet. A permission prompt will open - tap Allow."
  termux-setup-storage
  for _ in $(seq 1 30); do [ -w /sdcard ] && break; sleep 2; done
  [ -w /sdcard ] || { echo "Storage permission not granted; re-run after allowing it." >&2; exit 1; }
fi

say "Installing helper commands into ~/bin"
mkdir -p "$HOME/bin"
for f in "$HERE"/termux/bin/*; do
  install -m 700 "$f" "$HOME/bin/$(basename "$f")"
done

say "Drive settings"
mkdir -p "$HOME/sftpgo-ui"
if [ ! -f "$HOME/sftpgo-ui/drive.env" ]; then
  cp "$HERE/sftpgo/drive.env.example" "$HOME/sftpgo-ui/drive.env"
  echo "Created ~/sftpgo-ui/drive.env (edit it to customise)."
else
  echo "Keeping your existing ~/sftpgo-ui/drive.env"
fi
# shellcheck source=/dev/null
. "$HOME/sftpgo-ui/drive.env"
mkdir -p "$SFTPGO_DATA_PROVIDER__USERS_BASE_DIR"
touch "$SFTPGO_DATA_PROVIDER__USERS_BASE_DIR/.nomedia"

say "Customised web UI"
bash "$HERE/sftpgo/install-ui.sh"

say "Auto-start"
mkdir -p "$HOME/.termux/boot"
install -m 700 "$HERE/termux/boot/00-home-server" "$HOME/.termux/boot/00-home-server"
if ! grep -q 'phone-cloud-drive' "$HOME/.bashrc" 2>/dev/null; then
  cat "$HERE/termux/bashrc.snippet" >> "$HOME/.bashrc"
fi

say "Starting the drive"
pkill -f '^sftpgo serve' 2>/dev/null || true
sleep 1
"$HOME/bin/start-drive"
sleep 4
"$HOME/bin/drive-status" || true

cat <<'EOF'

Next steps (see docs/setup.md):
  1. Create the admin account on the phone's Wi-Fi address:  http://<phone-ip>:8080/web/admin
  2. Install the Termux:Boot add-on and open it once (auto-start after reboot).
  3. Optional: public HTTPS access -> bash server/cloudflared/setup-tunnel.sh
EOF
