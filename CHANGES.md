# Changes relative to upstream / Изменения относительно оригинала

Noctwade 1.0.0 (2026-10-27) is derived from [throneproj/ThroneForAndroid](https://github.com/throneproj/ThroneForAndroid) commit `5653af94e2b0d46a8ba34c606a09ade473c0c35d` (v2.0.3, "resync with desktop"). Under GPLv3 §5(a) this file states that the files were modified and when. The full diff is the git history of this repository (`git diff 5653af9..HEAD`). Roughly 190 upstream files were modified and 470 added.

## Branding and packaging
- App name Noctwade, package `com.noctwade.android`, new launcher icon (crescent moon and star), monochrome themed icon, notification and quick-settings tile icon.
- Version 1.0.0 (versionCode 128000); ARM64 build only; release-key signing; the in-app updater checks `nockwade/Noctwade` releases and pins the Noctwade signing certificate.
- About screen links to this fork and credits the upstream project.

## Interface
- New "glass" visual style, 14 colour themes with accents and wallpapers, optional live background, battery-saver mode (Auto / On / Off).
- Home screen: connection button with animated state, subscription and server cards, session timer; server list opens as a bottom sheet.
- Servers: group headers with counters, sorting (subscription order / ping / alphabetical), country flags (flag-icons), ping quality indicator, long-name marquee, long-press action menu.
- Menu with in-menu search, compact popup menus.
- All UI icons redrawn in a single outline style (Lucide).

## Routing
- Route profiles as a list; rule list in the v2rayNG style; one shared DNS for all route profiles (Cloudflare / Google / Quad9 / manual).
- Ready-made route templates with runtime rule-sets: OutIP lists (bundled snapshots) and RoscomVPN conversion (incl. repository branch fix `main` → `master`).
- App routing: explicit enable switch and bypass mode; **system apps are hidden by default and shown with a checkbox; the "Auto select" button was removed**; the header scrolls away with the list; fixed the list disappearing after returning to the screen (cross-fade race).
- `reject` rules no longer carry a stray `outbound_id`.

## Core and networking
- sing-box core updated to **1.15.0-alpha.13** (upstream `throneproj/Throne` core at `1f30af2`).
- The local mixed proxy (SOCKS/HTTP on 127.0.0.1) gets a random login/password on first launch, unless the user configured one or a system HTTP proxy / LAN access is on.
- A repeated close of the core's API listener ("use of closed network connection") is no longer reported as a failed stop.
- Traffic units are binary (1 GB = 1024³ B) and sized automatically.
- Ping check after connect runs once per connection.

## Build
- Updated dependencies (Material 1.12.0, AppCompat 1.7.0, Core 1.13.1, Fragment 1.8.5, RecyclerView 1.3.2, WorkManager 2.9.1, Coroutines 1.8.1, Gson 2.11.0, OkHttp 4.12.0, SnakeYAML 2.3); Robolectric unit tests; `throne.abis` / `throne.applicationId` / `throne.updateSignerSha256` Gradle properties.
- Removed upstream tooling files for AI coding agents (`.roo/`, `openspec/`); they are not part of the app.

## Removed
- Per-route-profile DNS editor (replaced by the shared DNS tab); bottom tab bar.
