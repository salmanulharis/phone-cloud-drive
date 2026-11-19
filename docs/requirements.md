# Requirements

## Hardware

| Item | Notes |
|---|---|
| Android phone | Android 7.0+ for the server (Termux). Android 8.0+ for the Drive Admin app. No root. 3 GB+ RAM and plenty of free storage recommended. |
| Charger | The phone stays plugged in. Use a charge limit if your ROM/vendor offers one (e.g. 80-85%) to protect the battery. |
| Wi-Fi | The phone needs a reliable connection. A DHCP reservation on your router keeps its local address stable. |
| A computer | Only for the one-time setup (ADB is optional) and for building the Android app. |
| Optional: UPS / power bank | Keeps the phone powered through outages, so it never needs to be unlocked after a power loss. |

## Accounts (only for public access)

| Account | Why |
|---|---|
| A domain name | For `drive.<your-domain>` / `dav.<your-domain>` |
| Cloudflare (free plan) | The domain's DNS must be on Cloudflare to use a named Cloudflare Tunnel |

Without these, the drive still works on your home network.

## Software on the phone

All installed by `server/install.sh` through Termux's package manager, except the two apps.

| Software | Version tested | Source |
|---|---|---|
| Termux | 0.118.3 | [GitHub releases](https://github.com/termux/termux-app/releases) or F-Droid (the Google Play build is limited) |
| Termux:Boot | 0.8.1 | [GitHub releases](https://github.com/termux/termux-boot/releases) or F-Droid. **Must come from the same source as Termux** (same signing key). |
| SFTPGo | 2.7.6 | `pkg install sftpgo` |
| cloudflared | 2026.11 | `pkg install cloudflared` |
| OpenSSH | 10.x | `pkg install openssh` |
| patch, sqlite, curl | any | `pkg install patch sqlite curl` |

> The web UI patches were made for SFTPGo **2.7.6**. `install-ui.sh` checks this and refuses to
> apply patches that don't fit, leaving the previous UI untouched. See
> [maintenance.md](maintenance.md#upgrading-sftpgo).

## Tools for building the Android app (on a computer)

| Tool | Version |
|---|---|
| JDK | 17 or newer (the JBR bundled with Android Studio works) |
| Android SDK | Platform 35, Build-Tools 35+ |
| Gradle | Provided by the wrapper (`android/gradlew`, Gradle 8.11.1) |
| Android Gradle Plugin / Kotlin | 8.7.3 / 2.1.20 (declared in `android/build.gradle.kts`) |
| ADB (optional) | To install the APK over USB |

Android Studio (any recent version) includes everything above.
