# Setup

This guide turns a phone into the drive server. Allow about 30 minutes.
Commands in `sh` blocks run **in Termux on the phone** unless stated otherwise.

- [1. Prepare the phone](#1-prepare-the-phone)
- [2. Install the server](#2-install-the-server)
- [3. Create the admin account and users](#3-create-the-admin-account-and-users)
- [4. Auto-start after reboot](#4-auto-start-after-reboot)
- [5. Optional: SSH from your computer](#5-optional-ssh-from-your-computer)
- [6. Optional: public HTTPS access](#6-optional-public-https-access)
- [7. Check everything](#7-check-everything)

## 1. Prepare the phone

1. **Update and reset (recommended).** A spare phone with a fresh install is easiest.
2. **Install Termux** from its [GitHub releases](https://github.com/termux/termux-app/releases)
   (pick the `arm64-v8a` APK for most phones) or from F-Droid.
   - Google Play Protect may warn that the app was "built for an older version of Android".
     Tap **More details → Install anyway**. (Termux targets an older SDK on purpose so it can run Linux programs.)
3. **Install Termux:Boot** from the **same source** as Termux (GitHub or F-Droid). Builds from
   different sources are signed with different keys and won't work together.
4. **Open Termux once** and wait for the `~ $` prompt (it downloads its base system).
5. **Open Termux:Boot once.** Android only lets it start at boot after it has been opened.
6. **Battery:** in **Settings → Apps → Termux → Battery**, choose **Unrestricted** (wording varies).
   Do the same for Termux:Boot.
7. **Charge limit (if available):** many ROMs (LineageOS-based: *Settings → Battery → Charging control*)
   and vendors offer a charge limit. 80-85% is a good value for an always-plugged phone.
8. **Stable address:** in the phone's Wi-Fi settings, set **Privacy → Use device MAC** for your network,
   then reserve its IP address in your router. Random MAC addresses give the phone a new local IP
   after each reboot.

## 2. Install the server

In Termux:

```sh
pkg install -y git
git clone https://github.com/salmanulharis/phone-cloud-drive.git
cd phone-cloud-drive
bash server/install.sh
```

The installer:

1. installs `openssh sftpgo cloudflared patch sqlite curl`
2. asks for **storage permission** (tap **Allow**)
3. copies the helper commands to `~/bin` (`start-drive`, `start-tunnel`, `drive-status`, `drive-signout-all`)
4. creates `~/sftpgo-ui/drive.env` from [`drive.env.example`](../server/sftpgo/drive.env.example)
5. builds the themed web UI in `~/sftpgo-ui` (patches SFTPGo's own templates, adds the theme)
6. creates the hidden drive folder `/sdcard/.CloudDrive` with a `.nomedia` marker
7. installs the Termux:Boot script and a `~/.bashrc` snippet
8. starts the drive and prints `drive-status`

It is safe to run again; existing settings are kept. Open a new Termux session (or run
`source ~/.bashrc`) so the helper commands in `~/bin` are on your `PATH`.

## 3. Create the admin account and users

Find the phone's Wi-Fi address (`drive-status` prints it), then on a computer **on the same Wi-Fi**:

1. Open `http://<phone-ip>:8080/web/admin`. The first visit asks you to **create the admin account**.
   Use a strong password; the admin can see all files.
2. **Users → Add**: enter a username and password. Leave **Home directory empty**: each user then gets
   their own folder `/sdcard/.CloudDrive/<username>`.
   - Optional: a **quota** (storage limit) and **permissions** (e.g. read-only).
3. Sign in as that user at `http://<phone-ip>:8080/web/client`.

You can also manage users with the [Drive Admin app](android-app.md).

## 4. Auto-start after reboot

Termux:Boot runs `~/.termux/boot/00-home-server` after every boot. It keeps the CPU awake,
waits for internet, and starts SSH, the drive and the tunnel. A log is written to `~/.boot.log`.

> **Screen lock:** with a PIN, pattern or password, Android keeps app data encrypted until the
> phone is unlocked **once** after power-on. The server starts about 15 seconds after that first
> unlock; you can lock the screen again straight away. With no secure lock, it starts by itself.

Test it: reboot, unlock once, wait 30 seconds, then run `drive-status` (or open the drive).

## 5. Optional: SSH from your computer

On the phone, set a key-only login (recommended) or a password:

```sh
# on your computer (PowerShell, macOS or Linux):
ssh-keygen -t ed25519 -f ~/.ssh/phone-drive -N ""
# copy the content of ~/.ssh/phone-drive.pub, then on the phone:
mkdir -p ~/.ssh && echo "ssh-ed25519 AAAA...your-key..." >> ~/.ssh/authorized_keys
chmod 700 ~/.ssh && chmod 600 ~/.ssh/authorized_keys
echo "PasswordAuthentication no" >> $PREFIX/etc/ssh/sshd_config
pkill sshd; sshd
```

Connect from your computer: `ssh -i ~/.ssh/phone-drive -p 8022 <phone-ip>` (any username works in Termux).

## 6. Optional: public HTTPS access

See [cloudflare-tunnel.md](cloudflare-tunnel.md). In short:

```sh
cp server/cloudflared/tunnel.env.example server/cloudflared/tunnel.env
nano server/cloudflared/tunnel.env        # your hostnames
bash server/cloudflared/setup-tunnel.sh   # opens a Cloudflare login link once
```

## 7. Check everything

```sh
drive-status
```

```
Services
  ● SSH server (port 8022)
  ● Drive / SFTPGo
  ● Cloudflare tunnel
Endpoints (local)
  ● Web UI   http://127.0.0.1:8080
  ● WebDAV   http://127.0.0.1:10080
...
```

Then:
- sign in at `https://drive.example.com` (or `http://<phone-ip>:8080/web/client` at home)
- upload a file, open it, create a share link
- map the WebDAV drive (Windows: *This PC → Map network drive → `https://dav.example.com`*)
