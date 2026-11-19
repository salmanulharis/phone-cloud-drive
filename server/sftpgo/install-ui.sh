#!/data/data/com.termux/files/usr/bin/bash
# Build the customised web UI in ~/sftpgo-ui from the templates/static files that ship
# with the installed SFTPGo package, then apply this project's patches and assets.
#
# SFTPGo's WebUI templates may only be used within SFTPGo, so this repository ships
# patches (not copies) and applies them to your own SFTPGo installation.
#
# Re-run after upgrading SFTPGo:  bash server/sftpgo/install-ui.sh
set -eu

HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$PREFIX/share/sftpgo"
DEST="$HOME/sftpgo-ui"
TESTED_VERSION="2.7.6"

command -v sftpgo >/dev/null || { echo "SFTPGo is not installed (pkg install sftpgo)" >&2; exit 1; }
command -v patch  >/dev/null || pkg install -y patch

VERSION=$(sftpgo --version | head -1 | awk '{print $2}' | cut -d- -f1)
if [ "$VERSION" != "$TESTED_VERSION" ]; then
  echo "Note: patches were made for SFTPGo $TESTED_VERSION, you have $VERSION. Trying anyway..."
fi

mkdir -p "$DEST"
rm -rf "$DEST/templates.new" "$DEST/static.new"
cp -r "$SRC/templates" "$DEST/templates.new"
cp -r "$SRC/static" "$DEST/static.new"

# Apply template patches (a dry run first, so a failed patch leaves the live UI untouched).
for p in "$HERE"/patches/*.patch; do
  if ! patch -s -p1 --dry-run -d "$DEST/templates.new" < "$p" >/dev/null; then
    echo "Patch $(basename "$p") does not apply to SFTPGo $VERSION." >&2
    echo "The previous UI in $DEST is unchanged. See docs/maintenance.md (Upgrading SFTPGo)." >&2
    rm -rf "$DEST/templates.new" "$DEST/static.new"
    exit 1
  fi
  patch -s -p1 -d "$DEST/templates.new" < "$p"
  echo "applied $(basename "$p")"
done

# Branding assets
mkdir -p "$DEST/static.new/drive"
cp "$HERE"/static/drive/* "$DEST/static.new/drive/"

rm -rf "$DEST/templates" "$DEST/static"
mv "$DEST/templates.new" "$DEST/templates"
mv "$DEST/static.new" "$DEST/static"
echo "Web UI installed in $DEST (restart the drive to load it)."
