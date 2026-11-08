```text
Write one AGSL fragment shader (Android 13, Skia SkSL dialect) for a flat animated halo ring around a round button in an Android app. Reply with ONE code block containing the complete shader and nothing else after it.

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

Optional line to append when the halo must animate a picture:

```text
Also declare `uniform shader iImage;`, a transparent picture of the ring (240x240). Spin it with `iImage.eval(center + rotated offset)` and add a soft glow and a sweeping light.
```
