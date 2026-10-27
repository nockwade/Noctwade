# Third-party material

Noctwade is licensed GPL-3.0 (see LICENSE). It includes or builds on:

| Component | Licence | Notes |
|---|---|---|
| [ThroneForAndroid](https://github.com/throneproj/ThroneForAndroid) | GPL-3.0 | upstream of this fork |
| SagerNet / NekoBox for Android, sing-box, [throneproj/Throne](https://github.com/throneproj/Throne) core, `throneproj/sing-box`, `throneproj/sing` | GPL-3.0 (see their repositories) | native core, linked unmodified |
| [flag-icons](https://github.com/lipis/flag-icons) | MIT | country flags, `assets/noctwade-flags/` (licence text and commit in that folder) |
| [Lucide](https://lucide.dev) | ISC | UI icons, `assets/licenses/lucide.txt` |
| [Unbounded](https://github.com/googlefonts/unbounded) | SIL OFL 1.1 | wordmark font, `assets/licenses/unbounded-OFL.txt` |
| [MetaCubeX/meta-rules-dat](https://github.com/MetaCubeX/meta-rules-dat) | GPL-3.0 | `meta-*.srs` rule-set snapshots |
| [OISD](https://oisd.nl) "big" list, compiled via [legiz-ru/mihomo-rule-sets](https://github.com/legiz-ru/mihomo-rule-sets) | see the publishers | `oisd-big.srs` snapshot |
| [hydraponique/roscomvpn-geosite / -geoip / -routing](https://github.com/hydraponique) | MIT (geosite) | RoscomVPN rule-sets fetched at run time; `roscom-*.srs` snapshots |
| [throneproj/routeprofiles](https://github.com/throneproj/routeprofiles) | see repository | fetched at build/run time |

Rule-set snapshots are in `app/src/main/assets/noctwade-rule-sets/` with URLs and checksums in `sources.json`. Route templates and the router UI follow the layout of [v2rayNG](https://github.com/2dust/v2rayNG) as an inspiration; no v2rayNG code is included. The wallpapers were drawn procedurally; some images were produced with an AI image generator.

"Throne", "NekoBox", "SagerNet" and "sing-box" belong to their respective owners. Noctwade is unofficial.
