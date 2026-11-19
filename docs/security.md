# Security

## What is protected

| Area | Measure |
|---|---|
| **User isolation** | Every user is confined to their own home folder on the web client, WebDAV and SFTP. Other users' files are only reachable through a share link the owner creates. Don't give two users the same home folder or a shared virtual folder unless you mean to. |
| **Admin access** | The admin panel and REST API are blocked by the tunnel (404) and only reachable on the home network. |
| **Transport (public)** | HTTPS to Cloudflare, then the encrypted tunnel to the phone. |
| **Cookies** | `HttpOnly`, `SameSite=Strict`, and `Secure` over HTTPS. |
| **Headers** | HSTS, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`, restrictive `Permissions-Policy`. |
| **Passwords** | Stored hashed by SFTPGo. Brute-force protection bans IPs after repeated failures, using the real visitor IP from Cloudflare. The client-IP header is trusted only from localhost, so it can't be spoofed from the LAN. |
| **At rest** | Android file-based encryption protects data while the phone is off or not yet unlocked after a boot. |
| **Media privacy** | Drive files are in a hidden folder with `.nomedia`, so Gallery and media apps don't index them. "Save to phone" in Drive Admin is the explicit way to make a copy public. |
| **Shares** | Optional password, expiry and usage limit; delete a share to revoke it immediately. |
| **App login** | Drive Admin's saved password is encrypted with a non-exportable Android Keystore key. |

## Known limitations

- **Logged-out cookies after a restart.** SFTPGo keeps revoked (logged-out) tokens in memory. Because
  the signing key is persistent, a cookie that was copied *before* logout becomes valid again after
  SFTPGo restarts, until it expires (max 12 h). The browser deletes its own cookie on logout, so this
  only matters if a cookie was stolen. If a device is lost, run `drive-signout-all`: it replaces the
  signing key and ends every session everywhere.
- **Home network is plain HTTP.** `http://<phone-ip>:8080` is unencrypted. Someone on the same Wi-Fi
  could capture traffic. Use the HTTPS hostname on networks you don't trust.
- **Cloudflare terminates TLS.** Traffic is decrypted at Cloudflare's edge (as with any
  Cloudflare-proxied site). It is not end-to-end encrypted.
- **Files are not encrypted per user.** Anyone using the unlocked phone, apps granted "All files
  access", or SSH/ADB users can read the drive folder. SFTPGo can store files encrypted
  (`cryptfs` filesystem) if you need that, at a performance cost and without thumbnails in the app.
- **Single copy.** Hardware failure or loss of the phone loses the data unless you keep backups.

## Recommendations

- Strong, unique passwords for the admin and every user; enable 2FA for users (web client → 2FA).
- Keep **USB debugging off** when you're not maintaining the phone.
- Use key-only SSH (`PasswordAuthentication no`).
- Keep `~/.sftpgo-signing.key`, `~/.cloudflared/*` and `tunnel.env` private. They're in `.gitignore`.
- Update regularly: `pkg upgrade` (then re-run `install-ui.sh`, see [maintenance](maintenance.md#upgrading-sftpgo)).
- Turn on the Cloudflare **Tunnel health alert** to learn quickly when the phone is offline.

## Reporting a vulnerability

Please open a private security advisory on the repository (**Security → Report a vulnerability**)
rather than a public issue.
