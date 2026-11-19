# Configuration

All drive settings live in **`~/sftpgo-ui/drive.env`** on the phone (created from
[`server/sftpgo/drive.env.example`](../server/sftpgo/drive.env.example)). Each line sets an
SFTPGo option through an environment variable: `SFTPGO_<SECTION>__<KEY>` maps to the key of the
same name in `$PREFIX/etc/sftpgo/sftpgo.json` (`__0__` = first item of a list).

After editing, restart the drive:

```sh
pkill -f '^sftpgo serve'; start-drive
```

## Settings

| Variable | Default here | Purpose |
|---|---|---|
| `SFTPGO_DATA_PROVIDER__USERS_BASE_DIR` | `/sdcard/.CloudDrive` | Parent folder of all user folders (hidden + `.nomedia`, so media apps ignore it) |
| `SFTPGO_WEBDAVD__BINDINGS__0__PORT` | `10080` | WebDAV port (SFTPGo's default is disabled) |
| `SFTPGO_HTTPD__TEMPLATES_PATH` | `$HOME/sftpgo-ui/templates` | Patched UI templates (built by `install-ui.sh`) |
| `SFTPGO_HTTPD__STATIC_FILES_PATH` | `$HOME/sftpgo-ui/static` | Static files incl. theme in `static/drive/` |
| `SFTPGO_DATA_PROVIDER__TRACK_QUOTA` | `1` | Track storage usage for all users (SFTPGo default `2` = only users with a quota) |
| `SFTPGO_HTTPD__SIGNING_PASSPHRASE_FILE` | `$HOME/.sftpgo-signing.key` | Persistent session-signing key, so logins survive restarts. Auto-generated, mode 600. |
| `SFTPGO_HTTPD__COOKIE_LIFETIME` | `720` | Web login lifetime in minutes. **720 is SFTPGo's maximum**; larger values are ignored (falls back to 20). Renewed while in use. |
| `SFTPGO_HTTPD__TOKEN_VALIDATION` | `1` | Don't bind a login to the client IP (phones switch networks) |
| `SFTPGO_HTTPD__BINDINGS__0__PROXY_ALLOWED` | `127.0.0.1,::1` | Only the local tunnel may set the client-IP header |
| `SFTPGO_HTTPD__BINDINGS__0__CLIENT_IP_PROXY_HEADER` | `CF-Connecting-IP` | Real visitor IP from Cloudflare (logs, brute-force protection) |
| `SFTPGO_WEBDAVD__BINDINGS__0__PROXY_ALLOWED` / `...CLIENT_IP_PROXY_HEADER` | same | Same for WebDAV |
| `SFTPGO_HTTPD__BINDINGS__0__SECURITY__ENABLED` | `true` | Enables the security headers below |
| `...SECURITY__HTTPS_PROXY_HEADERS__0__KEY/VALUE` | `X-Forwarded-Proto` / `https` | Detect HTTPS behind the tunnel → `Secure` cookies |
| `...SECURITY__STS_SECONDS` | `31536000` | HSTS (on HTTPS only) |
| `...SECURITY__CONTENT_TYPE_NOSNIFF` | `true` | `X-Content-Type-Options: nosniff` |
| `...SECURITY__REFERRER_POLICY` | `same-origin` | Referrer policy |
| `...SECURITY__PERMISSIONS_POLICY` | camera/mic/geo off | Permissions policy |
| `SFTPGO_COMMON__DEFENDER__ENABLED` | `true` | Brute-force protection |
| `SFTPGO_COMMON__DEFENDER__BAN_TIME` | `30` | Minutes an IP stays banned |
| `SFTPGO_COMMON__DEFENDER__THRESHOLD` | `15` | Score before a ban (a failed login scores 2) |
| `..._BRANDING__WEB_CLIENT__NAME` / `SHORT_NAME` | `Drive` | Product name in the user UI |
| `..._BRANDING__WEB_ADMIN__NAME` / `SHORT_NAME` | `Drive Admin` / `Admin` | Product name in the admin UI |
| `..._BRANDING__*__LOGO_PATH` / `FAVICON_PATH` | `/drive/logo.svg` / `/drive/favicon.png` | Logo and favicon (relative to the static path) |
| `..._BRANDING__*__EXTRA_CSS` | `/drive/drive.css` | Theme stylesheet |

Any other SFTPGo option can be added the same way; see the
[SFTPGo configuration docs](https://docs.sftpgo.com/latest/config-file/).

## Ports

| Port | Service | Reachable from |
|---|---|---|
| 8080 | Web client `/web/client`, admin `/web/admin`, REST API `/api/v2` | Home network; the tunnel exposes only `/web/client` |
| 10080 | WebDAV | Home network; tunnel (`dav.` hostname) |
| 2022 | SFTP | Home network |
| 8022 | SSH into Termux | Home network |

## Files and folders on the phone

| Path | Contents |
|---|---|
| `~/sftpgo-ui/drive.env` | Settings (this page) |
| `~/sftpgo-ui/templates`, `~/sftpgo-ui/static` | Themed UI built by `install-ui.sh` |
| `~/.sftpgo-signing.key` | Session-signing key (secret) |
| `~/.sftpgo.log` | SFTPGo log |
| `$PREFIX/var/lib/sftpgo/sftpgo.db` | Users, shares, admins (SQLite) |
| `$PREFIX/etc/sftpgo/sftpgo.json` | SFTPGo defaults (not edited; `drive.env` overrides) |
| `/sdcard/.CloudDrive/<user>/` | User files |
| `~/.cloudflared/` | Tunnel cert, credentials, `config.yml`, `tunnel.log` (secrets) |
| `~/.termux/boot/00-home-server` | Auto-start script; log in `~/.boot.log` |
| `~/bin/` | Helper commands |

## Theme

The look comes from [`server/sftpgo/static/drive/drive.css`](../server/sftpgo/static/drive/drive.css),
loaded after SFTPGo's own CSS. Brand colours are CSS variables at the top (`--bs-primary`, …) for
light and dark themes. Edit the copy in `~/sftpgo-ui/static/drive/` (or in the repo, then re-run
`install-ui.sh`); static files don't need a restart, just reload the page.
