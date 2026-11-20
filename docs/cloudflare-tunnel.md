# Public HTTPS access with Cloudflare Tunnel

> This guide is for the **Termux** setup. Using the Drive Admin app's built-in server? Follow
> [cloudflare-app.md](cloudflare-app.md) instead.

A named Cloudflare Tunnel gives the drive permanent HTTPS addresses such as
`https://drive.example.com` and `https://dav.example.com`, without opening router ports and
without exposing your home IP. `cloudflared` on the phone keeps an **outbound** connection to
Cloudflare; visitors connect to Cloudflare, which forwards requests through that connection.

## 1. Put your domain on Cloudflare DNS

Your domain can stay registered where it is (e.g. Namecheap, GoDaddy); only its **nameservers**
move to Cloudflare.

1. Create a free account at [dash.cloudflare.com](https://dash.cloudflare.com) → **Add a domain** →
   enter your domain → **Free** plan.
2. Cloudflare imports existing DNS records. **Compare them with your current provider** before
   switching, especially email records (`MX`, SPF/DKIM `TXT` such as `selector._domainkey`) and
   website records. Add anything that is missing.
3. For records that point at other hosting (e.g. GitHub Pages), set the proxy toggle to
   **DNS only** (grey cloud) unless you specifically want Cloudflare's proxy there.
4. At your registrar, replace the nameservers with the two Cloudflare nameservers shown.
5. Wait until Cloudflare shows the domain as **Active** (minutes to a few hours).

## 2. Create the tunnel

In Termux, from the repository folder:

```sh
cp server/cloudflared/tunnel.env.example server/cloudflared/tunnel.env
nano server/cloudflared/tunnel.env
```

```ini
TUNNEL_NAME=phone-drive
DRIVE_HOSTNAME=drive.example.com
DAV_HOSTNAME=dav.example.com      # leave empty if you don't want WebDAV publicly
```

```sh
bash server/cloudflared/setup-tunnel.sh
```

The script:
1. runs `cloudflared tunnel login` the first time. Open the printed link in a browser where you're
   signed in to Cloudflare, click your domain, then **Authorize**. This saves `~/.cloudflared/cert.pem`.
2. creates the tunnel (credentials in `~/.cloudflared/<tunnel-id>.json`)
3. creates the DNS records for your hostnames
4. writes `~/.cloudflared/config.yml` (see [`config.yml.example`](../server/cloudflared/config.yml.example))
5. starts the tunnel

`tunnel.env`, `cert.pem` and the credentials JSON are secrets for your account; they are listed in
`.gitignore` and must never be committed.

## What is exposed

| Path | Public? |
|---|---|
| `https://drive.example.com/web/client/...` (web drive, share links) | Yes, login required (shares: as configured) |
| `https://drive.example.com/web/admin/...` | **No**: answered with 404 by the tunnel |
| `https://drive.example.com/api/...` (REST API) | **No**: 404 |
| `https://dav.example.com/` (WebDAV) | Yes, login required |
| SFTP (port 2022), SSH (port 8022) | No, home network only |

## Limits and tips

- **100 MB per upload** on Cloudflare's free plan (per HTTP request). The web UI warns before
  uploading larger files; upload those from home at `http://<phone-ip>:8080`. Downloads are not affected.
- **QUIC blocked?** The config uses `protocol: http2`, which works on networks that block UDP.
- **Alerts when the phone goes offline:** Cloudflare dashboard → **Notifications → Add →
  Tunnel health alert** → choose your tunnel. You get an email if the phone loses power or restarts.
- **DNS caching:** right after creating the records, some resolvers may still answer from cache;
  allow up to a few hours.
- **Removing it:** `cloudflared tunnel delete <name>` and delete the DNS records in Cloudflare.
