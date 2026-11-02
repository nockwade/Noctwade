# AI prompt: a halo shader / Промт для нейросети: шейдер нимба

A halo is the animated ring around the connect button. Besides the built-in ones you can import your own: a small AGSL
shader (Android 13+, up to 256 KB; it runs on the GPU and cannot read files or use the network). Any AI chat can write
one with the prompt below. The full contract, rules and an example are in [HALO-SHADERS.md](../../HALO-SHADERS.md); the
same guide is in the app (**Halo** tab → **Shader guide**), where it can be copied or shared.

Нимб — анимированное кольцо вокруг кнопки подключения. Кроме встроенных можно импортировать свой: небольшой шейдер AGSL
(Android 13+, до 256 КБ; работает на видеочипе, не читает файлы и не выходит в сеть). Любой чат с ИИ напишет его по
промту ниже. Полный контракт, правила и пример — в [HALO-SHADERS.md](../../HALO-SHADERS.md); то же руководство есть в
приложении (вкладка **Нимб** → **Руководство по шейдерам**), его можно скопировать или отправить.

## How to use / Как пользоваться

1. Copy the prompt, replace the last line with the halo you want and send it to an AI chat.
2. Save the answer as a plain text file `my-halo.agsl`.
3. **Menu → Themes and personalization → Halo → the last tile "+ Add"** and pick the file. A `.zip` with several
   `.agsl` files or a whole theme pack works too.
4. If the app shows a compiler error, send the error and the shader back to the AI: "Fix this AGSL error and send the
   full corrected shader."
5. Tap the new tile to use it; press and hold to delete it.

1. Скопируйте промт, замените последнюю строку описанием нужного нимба и отправьте в чат с ИИ.
2. Сохраните ответ как обычный текстовый файл `my-halo.agsl`.
3. **Меню → Темы и персонализация → Нимб → последняя плитка «+ Добавить»** и выберите файл. Подойдёт и `.zip` с
   несколькими `.agsl`, и целый пакет тем.
4. Если приложение показало ошибку компилятора, отправьте ошибку и шейдер нейросети: «Исправь эту ошибку AGSL и пришли
   полный исправленный шейдер».
5. Нажмите на новую плитку, чтобы выбрать нимб; долгое нажатие — удалить.

---

## Prompt (English)

```text
Write one AGSL fragment shader (Android 13, Skia SkSL dialect) for a flat animated halo ring around a round button in an Android VPN app. Reply with ONE code block containing the complete shader and nothing else after it.

Rules:
1. The first line is a comment:  // name: <short name>
2. Declare exactly these uniforms (you may leave out ones you do not use):
   uniform float2 iResolution;  // 240 x 240
   uniform float iTime;         // seconds
   uniform float4 iColor;       // theme main colour, rgba 0..1
   uniform float4 iColor2;      // theme second colour
   uniform float iConnected;    // 0..1, 1 = connected
   uniform float iConnecting;   // 0..1, 1 = connecting
3. Entry point:  half4 main(float2 fragCoord)  returning a PREMULTIPLIED colour: half4(half3(rgb * alpha), half(alpha)).
4. Coordinates:  float2 p = (fragCoord - 0.5 * iResolution) / (0.5 * iResolution.y);  float d = length(p);  float ang = atan(p.y, p.x);
5. The button covers d < 0.62. Draw only between d = 0.65 and d = 1.0 and fade to nothing at both ends. The halo must be FLAT and face the viewer (a ring, not a tilted ellipse) and turn or pulse around the button.
6. Animate with iTime: rotation (use  ang + iTime * speed), shimmer, travelling highlights. Turn faster while iConnecting is near 1, and look slightly brighter when iConnected is near 1.
7. Use only plain SkSL: functions defined above main(), loops with constant bounds, no textures, no arrays of structs, no recursion, no variable named half/float/int. Write 1.0 - smoothstep(0.0, w, x), never smoothstep with reversed edges.
8. Keep it under 120 lines and cheap to run (a few sin/exp/smoothstep per pixel).

The halo I want: <DESCRIBE HERE: colours, shape, mood, what moves. For example "a gold double ring with small stars orbiting, calm and elegant">
```

## Промт (по-русски)

```text
Напиши один фрагментный шейдер AGSL (Android 13, диалект Skia SkSL) для плоского анимированного нимба-кольца вокруг круглой кнопки в Android-приложении VPN. Ответь ОДНИМ блоком кода с полным шейдером, после него ничего не пиши.

Правила:
1. Первая строка — комментарий:  // name: <короткое имя>
2. Объяви ровно эти uniform (ненужные можно опустить):
   uniform float2 iResolution;  // 240 x 240
   uniform float iTime;         // секунды
   uniform float4 iColor;       // основной цвет темы, rgba 0..1
   uniform float4 iColor2;      // второй цвет темы
   uniform float iConnected;    // 0..1, 1 = подключено
   uniform float iConnecting;   // 0..1, 1 = идёт подключение
3. Точка входа:  half4 main(float2 fragCoord)  возвращает ПРЕДУМНОЖЕННЫЙ цвет: half4(half3(rgb * alpha), half(alpha)).
4. Координаты:  float2 p = (fragCoord - 0.5 * iResolution) / (0.5 * iResolution.y);  float d = length(p);  float ang = atan(p.y, p.x);
5. Кнопка занимает d < 0.62. Рисуй только между d = 0.65 и d = 1.0 и плавно своди на нет на обоих краях. Нимб должен быть ПЛОСКИМ и смотреть на зрителя (кольцо, а не наклонный эллипс) и вращаться или пульсировать вокруг кнопки.
6. Анимируй через iTime: вращение (используй  ang + iTime * скорость), мерцание, бегущие блики. При iConnecting около 1 вращай быстрее, при iConnected около 1 делай чуть ярче.
7. Только простой SkSL: функции выше main(), циклы с постоянными границами, без текстур, без массивов структур, без рекурсии, без переменных с именами half/float/int. Пиши 1.0 - smoothstep(0.0, w, x), а не smoothstep с перевёрнутыми границами.
8. Уложись в 120 строк и делай дёшево (несколько sin/exp/smoothstep на пиксель).

Нужный мне нимб: <ОПИШИТЕ: цвета, форму, настроение, что движется. Например «золотое двойное кольцо с маленькими звёздами на орбите, спокойное и изящное»>
```

## A halo made from your own picture / Нимб из своей картинки

A shader may also animate a picture from a theme pack: declare `uniform shader iImage;` and read it with
`iImage.eval(uv)`; the pack's halo `image` (a flat ring seen from above, transparent PNG) is passed in, fitted to the
240 × 240 square. Add to the prompt: "Also declare `uniform shader iImage;` — a transparent picture of the ring, 240×240;
spin it with `iImage.eval(center + rotated offset)` and add a soft glow and a sweeping light."

Шейдер может оживить и картинку из пакета тем: объявите `uniform shader iImage;` и читайте её через `iImage.eval(uv)`;
в него приходит `image` нимба из пакета (плоское кольцо, вид сверху, прозрачный PNG), вписанное в квадрат 240 × 240.
Добавьте в промт: «Ещё объяви `uniform shader iImage;` — прозрачная картинка кольца 240×240; вращай её через
`iImage.eval(центр + повёрнутое смещение)` и добавь мягкое свечение и бегущий блик».
