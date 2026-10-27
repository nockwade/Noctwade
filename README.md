# Noctwade

**Noctwade is an unofficial fork of [throneproj/ThroneForAndroid](https://github.com/throneproj/ThroneForAndroid)** (an Android GUI proxy client powered by [sing-box](https://github.com/SagerNet/sing-box); formerly NekoBox for Android). It is **not affiliated with, endorsed by, or supported by** the Throne project, its authors, or SagerNet. Please do not report Noctwade problems to the upstream project.

> **Noctwade — неофициальный форк** [throneproj/ThroneForAndroid](https://github.com/throneproj/ThroneForAndroid). Он никак не связан с авторами Throne и SagerNet и не поддерживается ими. Проблемы Noctwade не нужно сообщать в оригинальный проект.

- Licence: **GNU GPL v3.0** (see [LICENSE](LICENSE)), same as upstream. All changes are published under the same licence.
- Base: upstream release [v2.0.4](https://github.com/throneproj/ThroneForAndroid/releases/tag/v2.0.4) (commit [`de0a8bd`](https://github.com/throneproj/ThroneForAndroid/commit/de0a8bd67a40d096418c565ebe388c4d506391a8)). Git history is preserved.
- What changed: [CHANGES.md](CHANGES.md). Third-party material: [THIRD-PARTY.md](THIRD-PARTY.md). Upstream README: [README.upstream.md](README.upstream.md).

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
apksigner verify --print-certs Noctwade-2.0.4-arm64-v8a.apk
```

The certificate SHA-256 must be:

```
65c97c27b88e53e24c60e1b4d6b62224a125d0f01cdcda0c847662b4a7f3e319
```

If the fingerprint differs, the file is not an official Noctwade build. The in-app updater pins this certificate. Builds are signed with a release key (not the public Android debug key); the key is held only by the maintainer.

- Package: `com.noctwade.android`; minSdk 24; three builds: `arm64-v8a` (most phones), `armeabi-v7a`, `x86_64`.
- It installs next to Throne (different package). It cannot update a build signed with another key: uninstall such a build first (export profiles first).

## Build from source / Сборка

Requirements: JDK 17+ (21 used), Android SDK 35 / build-tools 35.0.1, Go (see `core/go.mod` of the core) for the core library.

1. Build the core: the app embeds `ThroneCore.aar` (Go, gomobile, targets `android/arm64,android/arm,android/amd64`) built from [throneproj/Throne](https://github.com/throneproj/Throne) at the commit in `THRONE_CORE_REF` of [nb4a.properties](nb4a.properties), see the `.github/actions/throne-core` action. Place the result at `app/libs/ThroneCore.aar`.
2. Generate `app/src/main/assets/schema` (sing-box schema), `routeprofiles` and `sb-dashboard` like `.github/actions/*` does.
3. Build:

```
gradlew app:assembleOssRelease
```

Signing: set `SIGNING_KEYSTORE_B64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` (see `buildSrc`); pin the updater with `-Pthrone.updateSignerSha256=<cert sha256>`.

### Corresponding source of the core

The core in the release APK is the **unmodified** upstream [throneproj/Throne](https://github.com/throneproj/Throne) `core/` at commit `1f30af24e2de13e0735dede240e4592a93e18b64` (sing-box v1.15.0-alpha.13 via the `throneproj/sing-box` fork, commit `0b55bc5d0b1f`; `throneproj/sing`, commit `e3e392877db8`, see its `go.mod`). A source archive of that core is attached to each release as `ThroneCore-source.zip` (GPLv3 §6).

## Disclaimer

Provided "as is", without warranty (GPLv3 §15–16). For research and lawful use only; you are responsible for how you use it. See also the disclaimer in the [upstream README](README.upstream.md).
