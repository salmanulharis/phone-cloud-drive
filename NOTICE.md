# Third-party software and licences

This project's own code (scripts, configuration, patches, CSS, logo, documentation and
the Drive Admin Android app) is released under the [MIT License](LICENSE).

It builds on, but does **not** include, the following software. Each is installed
separately on your device and keeps its own licence:

| Software | Used for | Licence |
|---|---|---|
| [SFTPGo](https://github.com/drakkan/sftpgo) | File server: web UI, WebDAV, SFTP, users, shares | AGPL-3.0 (server). Its WebUI is based on KeenThemes templates whose use is restricted to SFTPGo (see below). |
| [cloudflared](https://github.com/cloudflare/cloudflared) | Cloudflare Tunnel client | Apache-2.0 |
| [Termux](https://github.com/termux/termux-app) and [Termux:Boot](https://github.com/termux/termux-boot) | Linux environment and auto-start on Android | GPL-3.0 |
| [OpenSSH](https://www.openssh.com/) | Remote shell into the phone | BSD-style |
| Jetpack Compose, AndroidX, Kotlin coroutines | Drive Admin app | Apache-2.0 |
| [ZXing](https://github.com/zxing/zxing) core | QR codes for the drive's addresses in the Drive Admin app | Apache-2.0 |

## The Drive Admin app's built-in server

`android/native/build.sh` downloads SFTPGo and cloudflared from their official repositories and
compiles them into the APK you build yourself (as `libsftpgo.so` and `libcloudflared.so`), together
with SFTPGo's own web templates patched as described below. Nothing of this is committed to the
repository (see `.gitignore`).

- **SFTPGo** is AGPL-3.0: if you distribute such an APK, you must also offer the corresponding
  source, i.e. SFTPGo's source at the pinned version plus this repository (build script and patches).
- **cloudflared** is Apache-2.0.
- SFTPGo's **web templates** may only be used within SFTPGo. They are used only by the bundled
  SFTPGo, but an APK containing them should be kept for your own use rather than published.

## About the SFTPGo web UI patches

SFTPGo's WebUI templates state that they may only be used within the SFTPGo product and
not in derivative works. For that reason this repository **does not redistribute those
templates**. `server/sftpgo/patches/` contains small patches that are applied, on your own
device, to the templates shipped with your SFTPGo installation, and are used only within
SFTPGo. If you are an SFTPGo/KeenThemes rights holder and have concerns, please open an issue.
