# Noctwade

**Noctwade is an unofficial fork of [throneproj/ThroneForAndroid](https://github.com/throneproj/ThroneForAndroid)** (an Android GUI proxy client powered by [sing-box](https://github.com/SagerNet/sing-box); formerly NekoBox for Android). It is **not affiliated with, endorsed by, or supported by** the Throne project, its authors, or SagerNet. Please do not report Noctwade problems to the upstream project.

> **Noctwade — неофициальный форк** [throneproj/ThroneForAndroid](https://github.com/throneproj/ThroneForAndroid). Он никак не связан с авторами Throne и SagerNet и не поддерживается ими. Проблемы Noctwade не нужно сообщать в оригинальный проект.

- Licence: **GNU GPL v3.0** (see [LICENSE](LICENSE)), same as upstream. All changes are published under the same licence.
- Base: upstream release [v2.0.5](https://github.com/throneproj/ThroneForAndroid/releases/tag/v2.0.5) (commit [`8d2f4c5`](https://github.com/throneproj/ThroneForAndroid/commit/8d2f4c5d655a9c9c87250684da04cc45c67b0a0a)). Git history is preserved.
- What changed: [CHANGES.md](CHANGES.md), per version: [CHANGELOG.md](CHANGELOG.md). Third-party material: [THIRD-PARTY.md](THIRD-PARTY.md). Upstream README: [README.upstream.md](README.upstream.md).

## Features / Возможности

Everything of ThroneForAndroid 2.0.5 (sing-box core, Xray, WireGuard/AmneziaWG, WARP, subscriptions, routing profiles, the Auto Selector, per-app proxy), plus a redesigned interface:

**Look and feel**
- **Themes**: 12 colour themes and your own colours (an accent and a second colour from a 36-swatch palette, saved for reuse); **Light / Dark / System**. A new theme spreads from the tapped control over the old one.
- **Backgrounds**: the theme gradient, your own 2–4 colour gradients (saved as tiles), a picture from the gallery, or backgrounds from theme packs; a calm animated sky (can be turned off; economy mode stops all motion).
- **Theme packs** (`.zip`): backgrounds, characters with animated sprites and a dialogue box on the home screen (status, timer and lines in the character's voice), and halo rings for the connect button. Noctwade ships no pack; make your own — see below.
- **Button halo**: the animated ring around the connect button — Classic, Rings, built-in shader halos, halos from packs, or your own **AGSL shaders** (Android 13+; import one `.agsl`, a `.zip` of many, or a pack). An in-app guide with an AI prompt can be copied or shared.
- **17 app icons**, chosen in the app.
- Glass cards, smooth screen transitions (a settings screen grows out of the tapped tile), one dialog style everywhere, a gentle start of the home screen.

**Using it**
- Home: connect button, subscription card (traffic, expiry), server card; the server list opens as a sheet with groups, sorting, country flags and ping quality.
- Menu with search; routing profiles import/export (JSON file or clipboard); per-app routing export/import; one shared DNS tab (Cloudflare / Google / Quad9 / manual).
- From upstream 2.0.5: per-group routing profiles, routing profiles from subscriptions, AmneziaWG mode for WARP, compact profile rows and group cards, Allow apps to bypass VPN, excluding countries in the Auto Selector (`!RU`), recovery after a network loss.

Всё из ThroneForAndroid 2.0.5 плюс новый интерфейс: 12 цветовых тем и свои цвета, светлая / тёмная / как в системе с плавной сменой от нажатой кнопки; фоны — градиент темы, свои градиенты, картинка из галереи, фоны из пакетов; **пакеты тем** с персонажами (анимированные спрайты и окно диалога на главном экране), фонами и нимбами; **нимб кнопки** — встроенные варианты, нимбы из пакетов и свои **шейдеры AGSL** (Android 13+); 17 иконок приложения; стеклянные карточки, единый стиль диалогов, плавные переходы; импорт/экспорт профилей маршрутизации и списка приложений; общий DNS. Из Throne 2.0.5 — маршрутизация по группам, профили маршрутизации из подписок, AmneziaWG для WARP, компактные строки и карточки, «Разрешить приложениям обходить VPN», исключение стран в автовыборе, восстановление после потери сети.

## Theme packs and halos / Пакеты тем и нимбы

| | |
|---|---|
| Pack format / Формат пакета | [THEME-PACKS.md](THEME-PACKS.md) |
| **AI prompt for a pack** / **Промт для создания пакета** | [docs/prompts/theme-pack-prompt.md](docs/prompts/theme-pack-prompt.md) |
| Halo shader guide / Руководство по шейдерам нимбов | [HALO-SHADERS.md](HALO-SHADERS.md) |
| **AI prompt for a halo shader** / **Промт для шейдера нимба** | [docs/prompts/halo-shader-prompt.md](docs/prompts/halo-shader-prompt.md) |

Import: **Menu → Themes and personalization → Background → Theme packs**, or the **Halo** tab → last tile **+ Add** (`.agsl`, `.zip` of shaders, or a pack). A long press deletes a single background, character or halo.

Импорт: **Меню → Темы и персонализация → Фон → Пакеты тем** или вкладка **Нимб** → последняя плитка **+ Добавить** (`.agsl`, `.zip` с шейдерами или пакет). Долгое нажатие удаляет отдельный фон, персонажа или нимб.

> Use only characters and art you made yourself or have the right to use, and do not share packs with other people's art. Packs are not part of Noctwade.
> Используйте только своих персонажей и арты, на которые у вас есть права, и не распространяйте пакеты с чужими артами. Пакеты не входят в Noctwade.

## Made with AI / Создано с помощью нейросетей

This fork was developed with heavy use of AI assistants (an AI coding assistant, Claude Code, and an AI image generator for some artwork). Code and assets have been checked by builds and automated tests only, **not by an independent security audit and not extensively on real devices**. Review the source before trusting it with anything sensitive.

Проект создан с активным использованием нейросетей (ИИ-помощник для кода, в том числе Claude Code, и генерация части изображений). Проверка выполнялась сборкой и автотестами; независимого аудита безопасности не было.

## Credits / Участники

- **[nockwade](https://github.com/nockwade)** — maintainer and author of Noctwade.
- **Claude (Anthropic)** and **Codex (OpenAI)** — AI assistants used to write and check code and assets; they are tools, not independent authors, and their output was directed and reviewed by the maintainer.
- **[throneproj / Throne](https://github.com/throneproj/ThroneForAndroid)**, **SagerNet / NekoBox** and the upstream contributors — the original application (GPL-3.0); see [AUTHORS](AUTHORS) and the git history.

## Download and verify / Скачать и проверить

Releases: <https://github.com/nockwade/Noctwade/releases>. Download only from there.

1. Compare the SHA-256 of the APK with `SHA256SUMS` from the same release.
2. Check the signing certificate:

```
apksigner verify --print-certs Noctwade-2.0.5-arm64-v8a.apk
```

The certificate SHA-256 must be:

```
65c97c27b88e53e24c60e1b4d6b62224a125d0f01cdcda0c847662b4a7f3e319
```

If the fingerprint differs, the file is not an official Noctwade build. The in-app updater pins this certificate. Builds are signed with a release key (not the public Android debug key); the key is held only by the maintainer.

- Package: `com.noctwade.android`; Android 7.0 (API 24) or newer. Files: `arm64-v8a` (most phones), `armeabi-v7a` (old and low-end phones with a 32-bit system), `x86_64` (emulators, Chromebooks), and `universal` (all of them in one file, bigger: take it when you are not sure which one you need).
- **"App not installed as it is incompatible with your phone"**: the installer's message is generic. Check, in order: the file is whole (its size and SHA-256 match `SHA256SUMS`; re-download it, a half-downloaded or renamed file fails this way); the phone has Android 7.0 or newer; the file's ABI is in the phone (`arm64-v8a` for almost every phone made after 2017, `armeabi-v7a` for a 32-bit system; the `universal` file has all of them); a copy of the app signed with another key is not installed (uninstall it, export profiles first). On Xiaomi / POCO (HyperOS, MIUI) also try installing from a file manager instead of a browser's download popup, and turn off "MIUI optimization" / "Install via USB" limits if they are on.
- It installs next to Throne (different package). It updates over Noctwade 2.0.4. It cannot update a build signed with another key: uninstall such a build first (export profiles first).

## Build from source / Сборка

Requirements: JDK 17+ (21 used), Android SDK 35, Go (see `core/go.mod` of the core) for the core library.

1. Build the core: the app embeds `ThroneCore.aar` (Go, gomobile, targets `android/arm64,android/arm,android/amd64`) built from [throneproj/Throne](https://github.com/throneproj/Throne) at the commit in `THRONE_CORE_REF` of [nb4a.properties](nb4a.properties), see the `.github/actions/throne-core` action. Place the result at `app/libs/ThroneCore.aar`.
2. Generate `app/src/main/assets/schema` (sing-box schema), `routeprofiles` and `sb-dashboard` like `.github/actions/*` does.
3. Build:

```
gradlew app:assembleOssRelease
```

Signing: set `SIGNING_KEYSTORE_B64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` (see `buildSrc`); pin the updater with `-Pthrone.updateSignerSha256=<cert sha256>`.

### Corresponding source of the core

The core in the release APK is the **unmodified** upstream [throneproj/Throne](https://github.com/throneproj/Throne) `core/` at commit `36b6bc4abd7792dcc1d533b02526b94b049c4a84` (sing-box v1.15.0-alpha.13 via the `throneproj/sing-box` fork, commit `af51a7460ccd`; `throneproj/sing` `e3e392877db8`, `throneproj/wireguard-go` `e11787583013`, `throneproj/xray-core` `d672759ff89f`; see its `go.mod`). A source archive of that core and its Android build script is attached to each release as `ThroneCore-source.zip` (GPLv3 §6).

## Disclaimer

Provided "as is", without warranty (GPLv3 §15–16). For research and lawful use only; you are responsible for how you use it. See also the disclaimer in the [upstream README](README.upstream.md).
