# Changes relative to upstream / Изменения относительно оригинала

Noctwade 2.0.6 (2026-11-06) is derived from [throneproj/ThroneForAndroid](https://github.com/throneproj/ThroneForAndroid) release [v2.0.6](https://github.com/throneproj/ThroneForAndroid/releases/tag/v2.0.6) (commit `e81549fb928c088d3a47e99d7552d721d3586111`; development started from `5653af94`, v2.0.3, and upstream v2.0.4, v2.0.5 and v2.0.6 were merged in). Under GPLv3 §5(a) this file states that the files were modified and when. The full diff is the git history of this repository (`git diff v2.0.6..HEAD` against upstream).

## 2.0.6 (2026-11-06)
- Based on upstream [v2.0.6](https://github.com/throneproj/ThroneForAndroid/releases/tag/v2.0.6) (commit `e81549fb`, core `THRONE_CORE_REF=cb89a14f`), merged into the Noctwade changes: DNS tunnels (DNSTT / VayDNS, MasterDNS / StormDNS, XDNS), mKCP in Xray, S3 storage for XDRIVE, improved WARP MASQUE (HTTP/3), newer sing-box / Xray / Go, a fix for the log export crashing in a loop. The merge was a plain three-way merge: no conflicts besides the fork's own README, CHANGELOG and `nb4a.properties` (which keeps the Noctwade package name).
- Also in this version (first shipped in the 2.0.5-pre.2 beta):
- Home: a "hide the interface" button (`header_focus`, between the title and +; `HomeFocus`): every child of the Home column except the dialogue box, and every header child except the button itself, the menu button and the title, fades out and becomes invisible (so it takes no touches); views that were hidden stay hidden when it comes back. The character layer grows about her feet to fill the screen and slides to the middle (`CharacterHome.zoom`; the scene stops clipping its children meanwhile, or the enlarged picture would be cut where the layer used to end), her dialogue has no card any more but sits on a dark grey fade along the bottom of the screen (a `scrim` view, 46% of the height) in light text, like a visual novel, the connect button with its halo stays where it is (the glass card and the text colours fade into the docked look; `ShimmerSurface` now honours `setAlpha`), and the hide button slides into the corner of the hidden + button (about half a second, none with animations off); all of it runs back when the interface returns. Taps on her are tested against where she is drawn. The icon changes from the four rounded corners to an eye (Lucide `scan` and `eye`). The menu stays usable while hidden and the system Back shows the interface first. Servers has no such button. Switch: Themes › More › Home screen (`focus_button`, on by default).
- Home: a banner under the top bar on the first start when HWID is off (`sub_send_hwid`): "Turn on" sets it, "Later" only closes the banner; either answer is remembered (`hwid_banner_done` in the theme preferences) and the banner is not shown again.
- Install: the default launcher alias (`AppIcon_default`) has no `icon`, `roundIcon` and `label` of its own (it takes the application's, the same ones). With them a POCO X6 Pro (HyperOS) refused 2.0.5 as "incompatible with your phone" while 2.0.4 installed; a build with this manifest installed. The generator `artwork/app_icons.py` writes the alias this way.
- Halo: turning by hand works on the whole ring - the touch asks scrolling parents to stay out at once (at the left and right the finger moves vertically and the parent took the gesture).
- Connect button: the white sheen on the disc and the white stop of the classic halo's sweep gradient are removed (no glints).
- Beta builds: the "preview version" notice is shown once per run of the app; recreating the screen after a theme or colour change no longer shows it again.
- Gradients: a full RGB colour picker (saturation/brightness plane, hue bar, R/G/B sliders and a hex field; `ColorEditor`) for each of up to four colours, with the ready swatches kept as shortcuts; the colours are stored exactly, not snapped to the nearest swatch.
- Connect button: the halo can be turned by hand like a spinner (drag along the ring; a flick keeps it turning and slows down, a soft click every 30 degrees). A tap still connects.
- Dialogues off: the character's dialogue box is gone completely; the status (and the timer) is written under the button, below the halo.
- After the app restarts itself (backup restore, reset settings, log level) with a connection running, the connection is put back.
- New setting "Block traffic while switching servers" (Settings › General, off by default): the old tun is kept up with nothing behind it until the new server's tun replaces it, so the real address cannot leak between the two.
- Servers: "Select servers" in a server's menu starts a picking mode (checks, count, select all, "Ping selected"); only the picked servers are tested.
- Release: an optional universal APK (every ABI in one file, `-Pthrone.universal=true`) for people who are not sure which file fits their phone; the README lists what to check when the installer says "incompatible with your phone".
## 2.0.5 (2026-11-03)
- Based on upstream [v2.0.5](https://github.com/throneproj/ThroneForAndroid/releases/tag/v2.0.5) (commit `8d2f4c5d`, core `THRONE_CORE_REF=36b6bc4a`), merged into the Noctwade changes: per-group routing profiles, routing profiles from subscriptions, AmneziaWG mode for WARP, compact profile rows and group cards, Allow apps to bypass VPN, country exclusion in the Auto Selector, connection recovery after a network loss, Xray fixes. Noctwade keeps one global DNS for every routing profile, so the upstream per-profile DNS note on the DNS screen is not taken; the upstream check that imported app lists hold package names is applied to Noctwade's per-app routing import.
- Themes: the 14 colour themes and their wallpapers are replaced by 12 colour presets and custom colours (an accent and a second colour from 36 swatches, with generated light and dark palettes, saved for reuse); appearance Light / Dark / System. A theme or mode change spreads from the tapped control over the old look (`ThemeReveal`).
- Personalization screen: four tabs (Themes, Background, Halo, More) in one segmented control; backgrounds, characters and halos as three-column tile grids with a check on the chosen one; changes apply without recreating the screen; pages are built when first opened.
- Backgrounds: theme gradient, custom 2–4 colour gradients (saved as tiles), gallery picture, theme-pack pictures.
- Theme packs: zip import (`ThemePacks`, format in THEME-PACKS.md) with backgrounds, characters and halos; Home shows the chosen character with a dialogue box carrying the status (always three lines tall, so the connect button never moves), or the plain status when none is chosen. A pack's single background, character or halo can be deleted by a long press. Noctwade ships no pack.
- Button halo: the ring around the connect button — Classic, Rings, No halo, built-in AGSL shader halos (Android 13+), halos from packs (a picture turned and lit by a shader) and imported shaders (`.agsl`, a `.zip` of many, or a whole pack; compiled on import and refused with the compiler message). In-app guide with an AI prompt (`HALO-SHADERS.md`).
- AI prompts for making packs and halo shaders: `docs/prompts/theme-pack-prompt.md`, `docs/prompts/halo-shader-prompt.md`.
- App icon: 17 launcher icons (default + 16) in a four-column grid (activity aliases; the launcher entry moved from `MainActivity` to the aliases; a launcher left without an enabled alias is repaired on start).
- Interface: one card style for every dialog including the settings text-field and multi-choice dialogs; a settings screen grows out of the tapped menu tile; Home fades in after its first frame and the system splash dissolves into it; softer screen, dialog and menu animations; clearer light theme (grey-tinted page, stronger edges on cards, buttons and segmented controls).
- Routing profiles: import from a JSON file or the clipboard; export a profile to a JSON file or copy it as JSON. Per-app routing: export / import (JSON file or clipboard) of the enable switch, bypass mode and package list; without access to installed apps it shows only the explanation and the settings button.
- Core: when the local proxy port is taken by another app, the core moves to a free port instead of failing to start.
- Updater: manifest `noctwade-update.json` (falls back to `throne-update.json`).
- Fixed: Dark → System on a dark system needed two taps and blinked; the app could show the wrong light/dark look after fast switching; buttons turned the stock purple after switching light/dark (AppCompat re-applied the bare base theme; `ThemedActivity.setTheme` now clears its theme id); a faint stripe on the animated background (fog and mist gradients were cut); a thin line flickering across Home under pack backgrounds (the fade mask left the last, fractional row of the picture); a bright smear on character tiles of a hidden page; halo animations that stopped when interface animations were off.
## Branding and packaging
- App name Noctwade, package `com.noctwade.android`, new launcher icon (crescent moon and star), monochrome themed icon, notification and quick-settings tile icon.
- Version 2.0.5, the same number as the upstream release it is based on (versionCode 111000); arm64-v8a, armeabi-v7a and x86_64 builds; release-key signing; the in-app updater checks `nockwade/Noctwade` releases and pins the Noctwade signing certificate.
- About screen links to this fork and credits the upstream project.

## Interface
- New "glass" visual style, 12 colour themes and custom colours, gradient / gallery / theme-pack backgrounds, theme packs with characters and button halos, 17 app icons, optional live background, battery-saver mode (Auto / On / Off).
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
- sing-box core **1.15.0-alpha.13** (upstream `throneproj/Throne` core at `36b6bc4`, unmodified).
- The local mixed proxy (SOCKS/HTTP on 127.0.0.1) gets a random login/password on first launch, unless the user configured one or a system HTTP proxy / LAN access is on.
- A repeated close of the core's API listener ("use of closed network connection") is no longer reported as a failed stop.
- Traffic units are binary (1 GB = 1024³ B) and sized automatically.
- Ping check after connect runs once per connection.

## Build
- Updated dependencies (Material 1.12.0, AppCompat 1.7.0, Core 1.13.1, Fragment 1.8.5, RecyclerView 1.3.2, WorkManager 2.9.1, Coroutines 1.8.1, Gson 2.11.0, OkHttp 4.12.0, SnakeYAML 2.3); Robolectric unit tests; `throne.abis` / `throne.applicationId` / `throne.updateSignerSha256` Gradle properties.
- Removed upstream tooling files for AI coding agents (`.roo/`, `openspec/`); they are not part of the app.

## Removed
- Per-route-profile DNS editor, new in upstream 2.0.4 (replaced by the shared DNS tab); bottom tab bar.
