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

## About the SFTPGo web UI patches

SFTPGo's WebUI templates state that they may only be used within the SFTPGo product and
not in derivative works. For that reason this repository **does not redistribute those
templates**. `server/sftpgo/patches/` contains small patches that are applied, on your own
device, to the templates shipped with your SFTPGo installation, and are used only within
SFTPGo. If you are an SFTPGo/KeenThemes rights holder and have concerns, please open an issue.
