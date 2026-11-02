# AI prompt: a theme pack / Промт для нейросети: пакет тем

A ready prompt for an image-capable AI chat (ChatGPT, Gemini and the like) that walks you through a complete Noctwade
theme pack: character sprites, backgrounds, halo rings for the connect button and the `pack.json` with dialogue. The
format itself is described in [THEME-PACKS.md](../../THEME-PACKS.md).

Готовый промт для нейросети, которая умеет рисовать (ChatGPT, Gemini и подобные). Он по шагам ведёт к полному пакету
тем Noctwade: спрайты персонажей, фоны, нимбы для кнопки подключения и `pack.json` с репликами. Сам формат описан в
[THEME-PACKS.md](../../THEME-PACKS.md).

> **Rights / Права.** Use only characters and art you made yourself or have permission to use. Do not ask the AI to copy
> characters from games, anime or other works, and do not share packs with other people's art.
> Используйте только своих персонажей и арты, на которые у вас есть права. Не просите нейросеть копировать персонажей
> из игр, аниме и других произведений и не распространяйте пакеты с чужими артами.

## How to use / Как пользоваться

1. Copy the prompt below (English or Russian), fill in the part **MY PACK / МОЙ ПАКЕТ** and send it.
2. The AI makes the pictures one by one; answer "next" / «дальше» after each. Save every picture with the file name it
   gives.
3. At the end it writes `pack.json`. Put everything into folders as in [THEME-PACKS.md](../../THEME-PACKS.md#structure--структура),
   zip the folder contents (`pack.json` at the root of the zip) and copy the zip to the phone.
4. In Noctwade: **Menu → Themes and personalization → Background → Theme packs → Import a pack (.zip)**. Halos also
   import from the **Halo** tab, last tile **+ Add**.
5. If the import refuses the pack, the message says what is wrong (a missing file, a picture too large, a bad id);
   paste it to the AI and ask it to fix `pack.json`.

---

## Prompt (English)

```text
You are helping me build a theme pack for the Android VPN app Noctwade. Follow the format exactly and work step by step:
make ONE image per reply and wait for me to write "next" before the following one.

MY PACK
- Pack id: [mypack]            (a-z, 0-9, - and _ only)
- Pack name: [My pack]         - Author: [me]
- Art style: [soft anime game style, cel shading, clean line art, pastel colours]
- Characters (0 to 12): [for each: id, name, a short description of look and personality,
  e.g. "mia — Mia, cheerful radio-club girl, short pink hair, headphones, school uniform"]
- Backgrounds (0 to 12): [for each: id, name, scene, e.g. "city — night city rooftop"]
- Halos for the connect button (0 to 12): [for each: id, name, look, e.g. "star — gold ring with small stars"]
- Languages for dialogue: [ru, en]
All characters and art must be original. Do not copy characters, logos or art from games, anime or other works.

STEP 1 — CHARACTER SPRITES
For every character make 4 images: idle, blink, happy, talk.
- idle: full body standing, front three-quarter view, looking at the viewer, both feet visible, centred, the
  character fills 90% of the height, vertical 1024x2048, TRANSPARENT background (or plain flat #F0F0F0 if transparency
  is impossible), no floor, no shadow, no text, no logo, one character only, correct hands.
- blink / happy / talk: EDIT the idle image and keep everything identical (pose, outfit, framing, size); change only
  the face: blink = eyes gently closed; happy = joyful closed-eye smile with a light blush; talk = eyes open, mouth
  slightly open as if speaking.
File names: characters/<id>/idle.png, blink.png, happy.png, talk.png

STEP 2 — BACKGROUNDS
For every background make day and night: wide scene 1536x1024 (landscape), no characters, no text, the main subject in
the upper half, a calm lower part (the app's cards cover it). Night = an edit of the day image: the same composition at
night, deep blue-violet sky, warm lights, not pitch black.
File names: backgrounds/<id>_day.png, backgrounds/<id>_night.png

STEP 3 — HALOS FOR THE CONNECT BUTTON
For every halo make one image: a FLAT halo ring seen from straight above (a perfect circle, not a tilted ellipse),
square 1024x1024, TRANSPARENT background, centred. The middle (62% of the radius) is covered by the round button, so
put every detail in the ring between 64% and 97% of the radius; nothing may touch the edges of the picture. Bright,
clean shapes with a soft glow; no text.
File names: halos/<id>.png

STEP 4 — pack.json
Write the complete pack.json (format 1) for everything above:
- names (and subtitles for characters) in every language as {"en": "...", "ru": "..."}
- backgrounds: "day", "night" and "fit": "top"
- characters: "background" = the most fitting background id, "colors" = two hex colours that match the character,
  "sprites" with the four files
- dialogue in every language, in the character's own voice, short (one or two lines each):
  idle 5, connecting 3, connected 5, stopping 2, empty 1 (asks to add a profile or a subscription), tap 6.
  The app is a VPN: lines may mention connecting, protection, the server; never ask for passwords or personal data.
- halos: {"id", "name", "image": "halos/<id>.png"}
Output only valid JSON in one code block.

Example of the shape (fill in your own values):
{
  "format": 1, "id": "mypack", "name": {"en": "My pack", "ru": "Мой пакет"}, "author": "me", "version": "1",
  "backgrounds": [{"id": "city", "name": {"en": "Night city", "ru": "Ночной город"},
                   "day": "backgrounds/city_day.png", "night": "backgrounds/city_night.png", "fit": "top"}],
  "characters": [{"id": "mia", "name": {"en": "Mia", "ru": "Мия"}, "subtitle": {"en": "Radio club", "ru": "Радиоклуб"},
                  "background": "city", "colors": ["#3FA9F5", "#FF9EC4"],
                  "sprites": {"idle": "characters/mia/idle.png", "blink": "characters/mia/blink.png",
                              "happy": "characters/mia/happy.png", "talk": "characters/mia/talk.png"},
                  "lines": {"en": {"idle": ["Shall we connect?"], "connecting": ["Connecting…"],
                                   "connected": ["Done, you're protected!"], "stopping": ["Disconnecting…"],
                                   "empty": ["Add a profile or a subscription first!"], "tap": ["Hehe!"]}}}],
  "halos": [{"id": "star", "name": {"en": "Star ring", "ru": "Звёздное кольцо"}, "image": "halos/star.png"}]
}
```

---

## Промт (по-русски)

```text
Ты помогаешь мне сделать пакет тем для Android-приложения VPN Noctwade. Строго соблюдай формат и работай по шагам:
одна картинка в одном ответе, следующую делай только после моего «дальше».

МОЙ ПАКЕТ
- id пакета: [mypack]            (только a-z, 0-9, - и _)
- Название: [Мой пакет]          - Автор: [я]
- Стиль: [мягкий аниме-стиль игры, cel shading, чистый лайн, пастельные цвета]
- Персонажи (от 0 до 12): [для каждого: id, имя, коротко внешность и характер,
  например «mia — Мия, весёлая девочка из радиоклуба, короткие розовые волосы, наушники, школьная форма»]
- Фоны (от 0 до 12): [для каждого: id, название, сцена, например «city — крыша ночного города»]
- Нимбы для кнопки подключения (от 0 до 12): [для каждого: id, название, вид, например «star — золотое кольцо со звёздами»]
- Языки реплик: [ru, en]
Все персонажи и рисунки должны быть оригинальными. Не копируй персонажей, логотипы и арты из игр, аниме и других
произведений.

ШАГ 1 — СПРАЙТЫ ПЕРСОНАЖЕЙ
Для каждого персонажа 4 картинки: idle, blink, happy, talk.
- idle: в полный рост, стоит, вид спереди в три четверти, смотрит на зрителя, обе ступни видны, по центру, персонаж
  занимает 90% высоты, вертикально 1024x2048, ПРОЗРАЧНЫЙ фон (или ровный #F0F0F0, если прозрачность невозможна),
  без пола, тени, текста и логотипов, один персонаж, правильные руки.
- blink / happy / talk: ОТРЕДАКТИРУЙ картинку idle, всё оставь как было (поза, одежда, кадр, размер), меняй только
  лицо: blink — глаза мягко закрыты; happy — радостная улыбка с закрытыми глазами и лёгким румянцем; talk — глаза
  открыты, рот слегка приоткрыт, как при разговоре.
Имена файлов: characters/<id>/idle.png, blink.png, happy.png, talk.png

ШАГ 2 — ФОНЫ
Для каждого фона день и ночь: широкая сцена 1536x1024 (горизонтально), без персонажей и текста, главное — в верхней
половине, нижняя часть спокойная (её закрывают карточки приложения). Ночь — правка дневной картинки: та же композиция
ночью, глубокое сине-фиолетовое небо, тёплые огни, не чёрная.
Имена файлов: backgrounds/<id>_day.png, backgrounds/<id>_night.png

ШАГ 3 — НИМБЫ ДЛЯ КНОПКИ ПОДКЛЮЧЕНИЯ
Для каждого нимба одна картинка: ПЛОСКОЕ кольцо-нимб, вид строго сверху (ровный круг, не наклонный эллипс),
квадрат 1024x1024, ПРОЗРАЧНЫЙ фон, по центру. Середину (62% радиуса) закрывает круглая кнопка, поэтому все детали —
в кольце между 64% и 97% радиуса, ничего не касается краёв картинки. Яркие чистые формы с мягким свечением, без текста.
Имена файлов: halos/<id>.png

ШАГ 4 — pack.json
Напиши полный pack.json (format 1) для всего перечисленного:
- имена (и подзаголовки у персонажей) на всех языках в виде {"en": "...", "ru": "..."}
- фоны: "day", "night" и "fit": "top"
- персонажи: "background" — id самого подходящего фона, "colors" — два hex-цвета под персонажа, "sprites" с четырьмя
  файлами
- реплики на всех языках, голосом самого персонажа, короткие (одна-две строки):
  idle 5, connecting 3, connected 5, stopping 2, empty 1 (просит добавить профиль или подписку), tap 6.
  Это VPN-приложение: можно говорить о подключении, защите, сервере; никогда не проси пароли и личные данные.
- нимбы: {"id", "name", "image": "halos/<id>.png"}
Выведи только корректный JSON одним блоком кода.

Пример формы (подставь свои значения):
{
  "format": 1, "id": "mypack", "name": {"en": "My pack", "ru": "Мой пакет"}, "author": "я", "version": "1",
  "backgrounds": [{"id": "city", "name": {"en": "Night city", "ru": "Ночной город"},
                   "day": "backgrounds/city_day.png", "night": "backgrounds/city_night.png", "fit": "top"}],
  "characters": [{"id": "mia", "name": {"en": "Mia", "ru": "Мия"}, "subtitle": {"en": "Radio club", "ru": "Радиоклуб"},
                  "background": "city", "colors": ["#3FA9F5", "#FF9EC4"],
                  "sprites": {"idle": "characters/mia/idle.png", "blink": "characters/mia/blink.png",
                              "happy": "characters/mia/happy.png", "talk": "characters/mia/talk.png"},
                  "lines": {"ru": {"idle": ["Подключимся?"], "connecting": ["Подключаюсь…"],
                                   "connected": ["Готово, ты под защитой!"], "stopping": ["Отключаюсь…"],
                                   "empty": ["Сначала добавь профиль или подписку!"], "tap": ["Хи-хи!"]}}}],
  "halos": [{"id": "star", "name": {"en": "Star ring", "ru": "Звёздное кольцо"}, "image": "halos/star.png"}]
}
```

## Animated halos / Анимированные нимбы

A picture halo turns slowly round the button. For a fully animated one, ask an AI for an AGSL shader with
[halo-shader-prompt.md](halo-shader-prompt.md), save it as `halos/<id>.agsl` and add `"shader": "halos/<id>.agsl"` next to
`"image"` (the picture is then used only on Android 12 and older).

Нимб-картинка медленно вращается вокруг кнопки. Для полностью анимированного попросите нейросеть написать шейдер AGSL
по промту [halo-shader-prompt.md](halo-shader-prompt.md), сохраните его как `halos/<id>.agsl` и добавьте
`"shader": "halos/<id>.agsl"` рядом с `"image"` (картинка тогда нужна только для Android 12 и ниже).
