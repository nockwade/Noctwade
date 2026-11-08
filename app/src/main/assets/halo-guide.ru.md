NOCTWADE · РУКОВОДСТВО ПО ШЕЙДЕРАМ НИМБОВ

ЧТО ЭТО
Нимб — кольцо вокруг большой кнопки подключения. Можно сделать свой: короткая программа (шейдер AGSL) рисует плоское кольцо или любой узор. Она работает на видеочипе, требует Android 13 и новее, не может читать ваши файлы и пользоваться сетью, размер — до 256 КБ.

В 5 ШАГОВ
1. Напишите шейдер или попросите нейросеть: скопируйте ПРОМТ из конца этого руководства в любой чат с ИИ и опишите нужный нимб.
2. Сохраните результат как обычный текстовый файл с расширением .agsl (например, my-halo.agsl).
3. Меню > Темы и персонализация > вкладка «Нимб» > последняя плитка «+ Добавить», выберите файл. Можно выбрать и .zip с несколькими шейдерами или целый пакет тем.
4. Если в шейдере ошибка, приложение покажет сообщение компилятора и не добавит его. Отправьте это сообщение нейросети с просьбой исправить шейдер и импортируйте снова.
5. Нажмите на новую плитку, чтобы выбрать нимб. Долгое нажатие на плитку — удалить.

ФАЙЛ
- В первых строках можно задать имя:  // name: Мой нимб
- Нужна функция  half4 main(float2 fragCoord)  с ПРЕДУМНОЖЕННЫМ цветом на выходе: half4(rgb * alpha, alpha).
- Объявляйте только нужные входы (uniform):

    uniform float2 iResolution;  // 240 x 240, квадрат, в котором вы рисуете
    uniform float  iTime;        // секунды, всегда идут
    uniform float4 iColor;       // основной цвет темы (r g b a, 0..1)
    uniform float4 iColor2;      // второй цвет темы
    uniform float  iConnected;   // 0..1 плавно: 1, когда подключено
    uniform float  iConnecting;  // 0..1 плавно: 1 во время подключения

КООРДИНАТЫ
    float2 p = (fragCoord - 0.5 * iResolution) / (0.5 * iResolution.y);
    float d = length(p);          // расстояние от центра, у края 1
    float ang = atan(p.y, p.x);   // угол вокруг центра
Диск кнопки занимает радиус 0 .. 0.62. Рисуйте между 0.65 и 1.0; всё дальше 1.0 обрезается. Плоское кольцо, крутящееся вокруг кнопки, — это кольцо на фиксированном d, узор которого зависит от  ang + iTime * скорость.

ПРАВИЛА, ЧТОБЫ НЕ БЫЛО ОШИБОК
- Пишите 1.0 - smoothstep(0.0, w, x), а не smoothstep(w, 0.0, x) (перевёрнутые границы не определены).
- Функции пишутся выше main(). У циклов постоянный предел: for (int i = 0; i < 12; i++).
- Не называйте переменную half, float, int и другими ключевыми словами.
- Заканчивайте предумноженным цветом, иначе у нимба будет тёмная кайма.
- Смешивайте iColor и iColor2, если нимб должен следовать теме, или берите свои цвета.
- Делайте легко: несколько sin / exp / smoothstep на пиксель.
- При выключенных анимациях и в экономном режиме iTime стоит на месте; нимб должен хорошо выглядеть и неподвижным.

МИНИМАЛЬНЫЙ ПРИМЕР
// name: Простое кольцо
uniform float2 iResolution;
uniform float iTime;
uniform float4 iColor;
uniform float iConnected;

half4 main(float2 fragCoord) {
    float2 p = (fragCoord - 0.5 * iResolution) / (0.5 * iResolution.y);
    float d = length(p);
    float line = 1.0 - smoothstep(0.0, 0.03, abs(d - 0.8));
    float glow = exp(-abs(d - 0.8) * 14.0) * 0.4;
    float alpha = clamp(line + glow, 0.0, 1.0) * (0.7 + 0.3 * iConnected);
    alpha *= smoothstep(0.62, 0.68, d) * (1.0 - smoothstep(0.96, 1.0, d));
    return half4(half3(iColor.rgb * alpha), half(alpha));
}

(«Сохранить пример шейдера» в приложении даёт большой готовый пример.)

ПАКЕТЫ ТЕМ
Пакет тем тоже может содержать нимбы: в pack.json добавьте  "halos": [ { "id": "mine", "name": "Мой нимб", "shader": "halos/mine.agsl", "image": "halos/mine.png" } ] . Шейдер используется на Android 13+, необязательная картинка (прозрачный PNG, рисуется за кнопкой) — на остальных. См. THEME-PACKS.md.

ПРОМТ ДЛЯ НЕЙРОСЕТИ (на английском, скопируйте всё между линиями)
------------------------------------------------------------
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
------------------------------------------------------------
