#!/data/data/com.termux/files/usr/bin/bash
# Create a named Cloudflare Tunnel for the drive and write ~/.cloudflared/config.yml.
# Prerequisites: your domain uses Cloudflare DNS; tunnel.env filled in (see tunnel.env.example).
# Usage: bash server/cloudflared/setup-tunnel.sh
set -eu

HERE="$(cd "$(dirname "$0")" && pwd)"
if [ ! -f "$HERE/tunnel.env" ]; then
  echo "Copy $HERE/tunnel.env.example to $HERE/tunnel.env and fill it in first." >&2
  exit 1
fi
# shellcheck source=/dev/null
. "$HERE/tunnel.env"
: "${TUNNEL_NAME:?TUNNEL_NAME is empty}" "${DRIVE_HOSTNAME:?DRIVE_HOSTNAME is empty}"
DAV_HOSTNAME="${DAV_HOSTNAME:-}"

command -v cloudflared >/dev/null || pkg install -y cloudflared
mkdir -p "$HOME/.cloudflared"

# 1. Authorise this phone for your Cloudflare zone (opens a login link once).
if [ ! -f "$HOME/.cloudflared/cert.pem" ]; then
  echo "Open the link below in a browser where you're signed in to Cloudflare and pick your domain:"
  cloudflared tunnel login
fi

# 2. Create the tunnel (or reuse an existing one with the same name).
if ! cloudflared tunnel list 2>/dev/null | awk 'NR>2 {print $2}' | grep -qx "$TUNNEL_NAME"; then
  cloudflared tunnel create "$TUNNEL_NAME"
fi
TUNNEL_ID=$(cloudflared tunnel list 2>/dev/null | awk -v n="$TUNNEL_NAME" '$2==n {print $1}')
[ -n "$TUNNEL_ID" ] || { echo "Could not find tunnel '$TUNNEL_NAME'" >&2; exit 1; }

# 3. DNS records pointing the hostnames at the tunnel.
cloudflared tunnel route dns "$TUNNEL_NAME" "$DRIVE_HOSTNAME"
[ -n "$DAV_HOSTNAME" ] && cloudflared tunnel route dns "$TUNNEL_NAME" "$DAV_HOSTNAME"

# 4. Tunnel configuration. The admin panel and API stay LAN-only.
{
  echo "tunnel: $TUNNEL_ID"
  echo "credentials-file: $HOME/.cloudflared/$TUNNEL_ID.json"
  echo "protocol: http2"
  echo "ingress:"
  echo "  - hostname: $DRIVE_HOSTNAME"
  echo "    path: ^/(web/admin|api/)"
  echo "    service: http_status:404"
  echo "  - hostname: $DRIVE_HOSTNAME"
  echo "    service: http://localhost:8080"
  if [ -n "$DAV_HOSTNAME" ]; then
    echo "  - hostname: $DAV_HOSTNAME"
    echo "    service: http://localhost:10080"
  fi
  echo "  - service: http_status:404"
} > "$HOME/.cloudflared/config.yml"

cloudflared tunnel --config "$HOME/.cloudflared/config.yml" ingress validate
"$HOME/bin/start-tunnel"
echo
echo "Tunnel '$TUNNEL_NAME' is running:"
echo "  web drive: https://$DRIVE_HOSTNAME"
[ -n "$DAV_HOSTNAME" ] && echo "  WebDAV:    https://$DAV_HOSTNAME"
