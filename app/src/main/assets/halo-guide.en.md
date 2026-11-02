NOCTWADE · HALO SHADER GUIDE

WHAT THIS IS
A halo is the ring around the big connect button. You can write your own: a short program (an AGSL shader) that paints a flat ring or any pattern. It runs on the GPU, needs Android 13 or newer, cannot read your files or use the network, and is limited to 256 KB.

IN 5 STEPS
1. Write the shader, or have an AI write it: copy the PROMPT at the end of this guide into any AI chat and describe the halo you want.
2. Save the result as a plain text file with the extension .agsl (for example my-halo.agsl).
3. Menu > Themes and personalization > Halo tab > the last tile "+ Add" and pick the file. It also takes a .zip with many shaders, or a whole theme pack.
4. If the shader has a mistake, the app shows the compiler's message and does not add it. Send that message back to the AI and ask it to fix the shader, then import again.
5. Tap the new tile to use it. Press and hold the tile to delete it.

THE FILE
- The first lines may name it:  // name: My halo
- It must contain  half4 main(float2 fragCoord)  and return a PREMULTIPLIED colour: half4(rgb * alpha, alpha).
- Declare only the inputs (uniforms) you use:

    uniform float2 iResolution;  // 240 x 240, the square you draw in
    uniform float  iTime;        // seconds, always running
    uniform float4 iColor;       // the theme's main colour (r g b a, 0..1)
    uniform float4 iColor2;      // the theme's second colour
    uniform float  iConnected;   // 0..1, eased: 1 when connected
    uniform float  iConnecting;  // 0..1, eased: 1 while connecting

COORDINATES
    float2 p = (fragCoord - 0.5 * iResolution) / (0.5 * iResolution.y);
    float d = length(p);          // distance from the centre, 0 .. 1 at the edge
    float ang = atan(p.y, p.x);   // angle round the centre
The button disc fills radius 0 .. 0.62. Draw between 0.65 and 1.0; anything past 1.0 is cut off. A flat ring turning round the button is simply a ring at a fixed d whose pattern depends on  ang + iTime * speed.

RULES THAT AVOID ERRORS
- Write 1.0 - smoothstep(0.0, w, x), never smoothstep(w, 0.0, x) (reversed edges are undefined).
- Functions must be written above main(). Loops need a constant limit: for (int i = 0; i < 12; i++).
- Do not name a variable half, float, int or another keyword.
- Always finish with premultiplied colour, or the halo gets a dark fringe.
- Mix iColor and iColor2 if the halo should follow the theme, or use your own colours.
- Keep it light: a few sin / exp / smoothstep per pixel.
- With animations off or in economy mode iTime stands still; make the halo look fine frozen.

MINIMAL EXAMPLE
// name: Simple ring
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

("Save an example shader" in the app writes a bigger, finished one.)

THEME PACKS
A theme pack can carry halos too: in pack.json add  "halos": [ { "id": "mine", "name": "My halo", "shader": "halos/mine.agsl", "image": "halos/mine.png" } ] . The shader is used on Android 13+, the optional picture (transparent PNG, drawn behind the button) elsewhere. See THEME-PACKS.md.

PROMPT FOR AN AI (copy everything between the lines)
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

If the app reports a compiler error, paste the message and the shader back to the AI with: "Fix this AGSL error and send the full corrected shader."
