```text
You are helping me build a theme pack for the Android app Noctwade. Follow the format exactly and work step by step:
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
