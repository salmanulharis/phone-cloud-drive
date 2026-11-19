# Maintenance

## Everyday commands (Termux)

| Command | What it does |
|---|---|
| `drive-status` | Services, local endpoints, storage, Wi-Fi address, last boot |
| `start-drive` | Start SFTPGo if it isn't running |
| `start-tunnel` | Start the Cloudflare tunnel if it isn't running |
| `pkill -f '^sftpgo serve'; start-drive` | Restart the drive (after changing `drive.env` or templates) |
| `pkill -f '^cloudflared .*tunnel.* run'; start-tunnel` | Restart the tunnel |
| `drive-signout-all` | Sign out every web session on every device |

## Logs

| Log | Path |
|---|---|
| SFTPGo | `~/.sftpgo.log` (JSON lines; `tail -f ~/.sftpgo.log`) |
| Tunnel | `~/.cloudflared/tunnel.log` |
| Boot | `~/.boot.log` (one block per boot) |

Useful filters:

```sh
grep '"sender":"login"' ~/.sftpgo.log | tail          # successful logins (user, IP)
grep 'connection_failed' ~/.sftpgo.log | tail          # failed logins
grep -E '"level":"(error|warn)"' ~/.sftpgo.log | tail  # problems
```

## Users

Use the admin panel (`http://<phone-ip>:8080/web/admin`) or the Drive Admin app:
- add or disable users, set quotas and permissions, reset passwords
- **Recalculate usage** if a user's usage looks wrong (e.g. after files were changed directly on the phone)

## Upgrading SFTPGo

```sh
pkg upgrade
cd ~/phone-cloud-drive && git pull       # get updated patches, if any
bash server/sftpgo/install-ui.sh
pkill -f '^sftpgo serve'; start-drive
```

`install-ui.sh` builds the UI from the **new** package's templates. If a patch no longer fits,
it stops and leaves the previous UI in place; the drive keeps working with the old templates.
To fix it, regenerate the patch against the new templates (`diff -u original modified`) or
temporarily point `SFTPGO_HTTPD__TEMPLATES_PATH` at `$PREFIX/share/sftpgo/templates` to use the
stock UI.

## Backups

The drive is the only copy of your data. Back up regularly:

```sh
# database (users, shares), settings and signing key
sqlite3 $PREFIX/var/lib/sftpgo/sftpgo.db ".backup '/sdcard/Download/sftpgo-backup.db'"
tar czf /sdcard/Download/drive-config.tgz -C ~ sftpgo-ui/drive.env .sftpgo-signing.key .cloudflared
```

Files: use the web client's multi-select **Download** (zip), sync the WebDAV drive with a tool
such as rclone or FreeFileSync, or copy `/sdcard/.CloudDrive` over USB/SSH (`scp -P 8022 -r`).

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Nothing works after a reboot | The phone hasn't been unlocked since power-on (secure lock screen). Unlock once; services start ~15 s later. |
| `ssh` or `http://<phone-ip>` stopped working after a reboot | The phone got a new local IP (random Wi-Fi MAC). Use *Privacy → Use device MAC* and a router DHCP reservation. `drive-status` shows the current address. |
| Public site shows Cloudflare error 1033/530 | Tunnel not running: `start-tunnel`, check `~/.cloudflared/tunnel.log`. |
| Upload fails for big files on the public site | Cloudflare's 100 MB request limit. Upload at home via `http://<phone-ip>:8080`. |
| "Invalid credentials" for a correct password | IP may be banned after many failures (30 min), or a typo. Check `connection_failed` in the log. |
| Signed out after each restart | `SFTPGO_HTTPD__SIGNING_PASSPHRASE_FILE` missing or the key file unreadable; check `drive.env`. |
| Sessions end after 20 min | `SFTPGO_HTTPD__COOKIE_LIFETIME` above 720 is ignored by SFTPGo; use ≤ 720. |
| Services stop when the screen is off | Battery optimisation: set Termux to *Unrestricted*; make sure `termux-wake-lock` ran (Termux notification shows "wake lock held"). |
| Termux:Boot doesn't run | Open the Termux:Boot app once; Termux and Termux:Boot must come from the same source (GitHub/F-Droid). |
| A user's usage shows 0 B | Set `SFTPGO_DATA_PROVIDER__TRACK_QUOTA=1`, restart, then *Recalculate usage*. |
| Photos appear in Gallery | Make sure `/sdcard/.CloudDrive/.nomedia` exists; Gallery may need a few minutes to rescan. |
