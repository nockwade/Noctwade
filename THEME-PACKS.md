# Theme packs / Пакеты тем

Noctwade itself ships only colour themes and gradient backgrounds. **Theme packs** add backgrounds and characters
(sprites, names, dialogue) and halo rings for the connect button. A pack is a `.zip`; import it in **Menu → Themes and
personalization → Background → Theme packs → Import a pack (.zip)** (or from the **Halo** tab, last tile **+ Add**).
After import its backgrounds and characters appear on the **Background** tab and its halos on the **Halo** tab; any
background can be combined with any character and any colour theme.

**Make a pack with an AI:** [docs/prompts/theme-pack-prompt.md](docs/prompts/theme-pack-prompt.md) (a step-by-step prompt
in English and Russian). **Animated halos:** [docs/prompts/halo-shader-prompt.md](docs/prompts/halo-shader-prompt.md).

Noctwade сам содержит только цветовые темы и градиентные фоны. **Пакеты тем** добавляют фоны и персонажей
(спрайты, имена, диалоги) и нимбы для кнопки подключения. Пакет — это `.zip`; импорт: **Меню → Темы и персонализация →
Фон → Пакеты тем → Импортировать пакет (.zip)** (или на вкладке **Нимб**, последняя плитка **+ Добавить**). После
импорта фоны и персонажи пакета появляются на вкладке **Фон**, нимбы — на вкладке **Нимб**; любой фон сочетается с
любым персонажем и любой цветовой темой.

**Сделать пакет с нейросетью:** [docs/prompts/theme-pack-prompt.md](docs/prompts/theme-pack-prompt.md) (пошаговый промт
на английском и русском). **Анимированные нимбы:** [docs/prompts/halo-shader-prompt.md](docs/prompts/halo-shader-prompt.md).

> Use only art you made yourself or have permission to use. Do not share packs with other people's art.
> Используйте только свои арты или арты, на которые есть разрешение. Не распространяйте пакеты с чужими артами.

## Structure / Структура

```
mypack.zip
├── pack.json
├── backgrounds/
│   ├── city_day.webp
│   └── city_night.webp
└── characters/
    └── mia/
        ├── idle.webp     (required / обязательно)
        ├── blink.webp    (optional: eyes closed / глаза закрыты)
        ├── happy.webp    (optional: joyful face / радость)
        └── talk.webp     (optional: mouth open / рот открыт)
```

The files may also sit in one top-level folder inside the zip. Only `pack.json`, images (`.webp`, `.png`,
`.jpg`) and halo shaders (`.agsl`) are read; everything else is ignored.

## pack.json

```json
{
  "format": 1,
  "id": "mypack",
  "name": { "en": "My pack", "ru": "Мой пакет" },
  "author": "Your name",
  "version": "1",
  "backgrounds": [
    {
      "id": "city",
      "name": { "en": "Night city", "ru": "Ночной город" },
      "day": "backgrounds/city_day.webp",
      "night": "backgrounds/city_night.webp",
      "fit": "top"
    }
  ],
  "characters": [
    {
      "id": "mia",
      "name": { "en": "Mia", "ru": "Мия" },
      "subtitle": { "en": "Star Academy · Radio club", "ru": "Звёздная академия · Радиоклуб" },
      "background": "city",
      "colors": ["#3FA9F5", "#FF9EC4"],
      "sprites": {
        "idle": "characters/mia/idle.webp",
        "blink": "characters/mia/blink.webp",
        "happy": "characters/mia/happy.webp",
        "talk": "characters/mia/talk.webp"
      },
      "lines": {
        "ru": {
          "idle": ["Подключимся?", "Я готова!"],
          "connecting": ["Подключаюсь…"],
          "connected": ["Готово, ты под защитой!"],
          "stopping": ["Отключаюсь…"],
          "empty": ["Сначала добавь профиль или подписку!"],
          "tap": ["Хи-хи!", "Щекотно!"]
        },
        "en": {
          "idle": ["Shall we connect?"],
          "connecting": ["Connecting…"],
          "connected": ["Done, you're protected!"],
          "stopping": ["Disconnecting…"],
          "empty": ["Add a profile or a subscription first!"],
          "tap": ["Hehe!"]
        }
      }
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `format` | Always `1`. |
| `id` | `a-z`, `0-9`, `-`, `_`, up to 40 characters. Importing a pack with the same id replaces it. Ids of backgrounds and characters follow the same rule. |
| `name`, `subtitle`, `author` | A plain string or `{ "en": …, "ru": … }`; the device language is used, then English, then any. |
| `backgrounds[].day` / `night` | `night` is optional; without it the day picture is dimmed in dark mode. |
| `backgrounds[].fit` | `"top"`: the picture fills the width at the top and fades into the theme (for landscape scenes, about 1080×1500). `"cover"`: fills the screen (portrait pictures, 1080×2400). |
| `characters[].background` | Suggested background id from the same pack (used for previews). |
| `characters[].colors` | Optional hint colours (#RRGGBB). |
| `characters[].sprites` | Full-body PNG/WebP **with transparent background**, the same canvas size and pose for all frames, only the face changes. Recommended height 1200–1600 px. |
| `characters[].halo` | Optional halo floating above her head: `{ "shader": "characters/mia/halo.agsl" }` (AGSL, Android 13+, up to 256 KB) or `{ "image": "characters/mia/halo.png" }` (transparent PNG/WebP, turns slowly), with `x`, `y` (centre on the sprite, 0..1; `y` may be slightly negative = above the picture) and `size` (width relative to the sprite, 0.1–1.2). A shader gets `iColor`/`iColor2` from the character's `colors`. Shader contract and examples: `HALO-SHADERS.md`. |
| `characters[].lines` | Per language: `idle`, `connecting`, `connected`, `stopping`, `empty` (no profile) and `tap` (when tapped) — each a list of lines (up to 60, 280 characters each). Missing groups fall back to the plain status text. |

Limits: up to 600 files, 25 MB per file, 160 MB in total, images up to 6000 px per side.

## Halo rings for the button / Нимбы-кольца для кнопки

A pack may also (or only) carry `halos`: transparent pictures that become selectable rings around the connect button
(**Themes and personalization → Halo**). They are independent of the characters, so a pack can hold only halos.

Пакет может содержать (в том числе только) `halos` — прозрачные картинки, которые выбираются как кольцо вокруг кнопки
подключения (**Темы и персонализация → Нимб**). Они не связаны с персонажами; пакет может состоять только из нимбов.

```json
{
  "format": 1,
  "id": "myhalos",
  "name": { "en": "My halos", "ru": "Мои нимбы" },
  "halos": [
    { "id": "ring1", "name": { "en": "Ring", "ru": "Кольцо" }, "shader": "halos/ring1.agsl", "image": "halos/ring1.png" }
  ]
}
```

A halo has a `shader`, an `image`, or both:
- `shader`: an AGSL file (up to 256 KB) drawn as a flat ring that turns round the button; Android 13+. The contract and a
  prompt for an AI are in `HALO-SHADERS.md`.
- `image`: a transparent picture drawn behind the button disc in its own shape (not stretched), swaying gently. It is the
  fallback where shaders cannot run (Android below 13); alone, it is the halo. Trim the empty margin; 640 px is plenty.

Long-press a pack's background, character or halo in the settings to delete just that entry.

У нимба есть `shader`, `image` или оба поля: `shader` — файл AGSL (до 256 КБ), плоское кольцо, вращающееся вокруг
кнопки (Android 13+; контракт и промт для нейросети — в `HALO-SHADERS.md`); `image` — прозрачная картинка за кнопкой в
своей форме, запасной вариант для Android ниже 13. Долгое нажатие на фон, персонажа или нимб пакета в настройках
удаляет только эту запись.

## Making the art / Как сделать картинки

1. **Sprites.** Generate (or draw) one full-body picture of the character, then edit **that same picture** to get
   `blink` (eyes closed), `happy` (closed-eye smile) and `talk` (mouth slightly open). Remove the background (most
   generators can output a transparent PNG; otherwise use any background remover). Crop all four frames with the
   **same** box so the body does not jump when the face changes.
2. **Backgrounds.** A landscape scene works best with `"fit": "top"`; keep the important part in the upper half,
   the lower part is covered by cards. Make the night version by editing the day picture ("same scene at night").
3. **Lines.** Short (one or two lines), in the character's voice; 3–6 per group feels alive. The character also
   speaks an `idle`/`connected` line by herself every 30–50 seconds and never repeats the last line.
4. Zip the files with `pack.json` at the root, copy the zip to the phone and import it.

## AI prompt for a complete pack / Промт для нейросети

The prompt that makes the sprites, backgrounds, halos and `pack.json` step by step is in
[docs/prompts/theme-pack-prompt.md](docs/prompts/theme-pack-prompt.md). For animated halos (AGSL shaders) use
[docs/prompts/halo-shader-prompt.md](docs/prompts/halo-shader-prompt.md).

Промт, который по шагам делает спрайты, фоны, нимбы и `pack.json`, — в
[docs/prompts/theme-pack-prompt.md](docs/prompts/theme-pack-prompt.md). Для анимированных нимбов (шейдеры AGSL) —
[docs/prompts/halo-shader-prompt.md](docs/prompts/halo-shader-prompt.md).