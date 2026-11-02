package io.nekohasekai.sagernet.ui.noctwade

import android.content.Context
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.io.InputStream
import java.util.Locale

/**
 * Halo shaders. A halo is drawn by an AGSL fragment shader (Android 13+) into a square: around the
 * connect button, or above a theme-pack character's head. Noctwade ships a few original shaders
 * (`assets/halo-shaders`); the user can import their own `.agsl` files. The contract is documented in
 * HALO-SHADERS.md. Shaders run in the GPU sandbox (no files, no network), and one that fails to
 * compile is refused at import and, if it ever fails at run time, replaced by the classic ring.
 */
internal object HaloShaders {
    const val CLASSIC = "classic"
    const val RINGS = "rings"
    const val NONE = "none"
    const val BUILTIN = "builtin:"
    const val IMPORTED = "shader:"

    /** A picture ring from a theme pack: `pack:<pack>/<id>`. */
    const val PACK = "pack:"
    const val MAX_SOURCE = 256 * 1024
    private const val MAX_IMPORTED = 60
    private const val ASSETS = "halo-shaders"
    private val ID = Regex("[a-z0-9][a-z0-9_-]{0,39}")
    private val UNIFORM = Regex("""uniform\s+\w+\s+(\w+)\s*;""")
    private val MAIN = Regex("""\bmain\s*\(""")

    class ImportException(message: String) : Exception(message)

    /** A halo shader the user can pick. [key] is what is stored in the settings. */
    class Entry(val key: String, val name: String, val builtIn: Boolean)

    /** Shaders need Android 13; below that only the canvas styles work. */
    fun supported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun root(context: Context) = File(context.filesDir, "halo-shaders")

    fun builtIn(context: Context): List<Entry> =
        runCatching { context.assets.list(ASSETS).orEmpty() }
            .getOrDefault(emptyArray())
            .filter { it.endsWith(".agsl") }
            .sorted()
            .map { file ->
                val id = file.removeSuffix(".agsl")
                Entry(BUILTIN + id, name(read(context, BUILTIN + id).orEmpty(), id), true)
            }

    fun imported(context: Context): List<Entry> =
        root(context)
            .listFiles { f -> f.isFile && f.name.endsWith(".agsl") }
            .orEmpty()
            .sortedBy { it.name }
            .map { f -> Entry(IMPORTED + f.name.removeSuffix(".agsl"), name(f.readText(), f.name.removeSuffix(".agsl")), false) }

    fun all(context: Context) = builtIn(context) + imported(context)

    /** Shader text for a stored key, or null for the canvas styles and unknown keys. */
    fun read(context: Context, key: String): String? =
        when {
            key.startsWith(BUILTIN) ->
                key.removePrefix(BUILTIN).takeIf { ID.matches(it) }?.let {
                    runCatching { context.assets.open("$ASSETS/$it.agsl").use { s -> s.readBytes().decodeToString() } }.getOrNull()
                }
            key.startsWith(PACK) -> ThemePacks.halo(context, key.removePrefix(PACK))?.shader?.takeIf(File::isFile)?.readText()
            key.startsWith(IMPORTED) ->
                key.removePrefix(IMPORTED).takeIf { ID.matches(it) }?.let { File(root(context), "$it.agsl").takeIf(File::isFile)?.readText() }
            else -> null
        }

    /** `// name: Title` in the first lines, or [fallback]. */
    fun name(source: String, fallback: String): String =
        source.lineSequence().take(8).mapNotNull { Regex("""^\s*//\s*name\s*:\s*(.+)$""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.trim() }
            .firstOrNull()
            ?.take(40)
            ?.ifBlank { null }
            ?: fallback

    /** Uniforms declared in [source]; only these are set (a shader may use any subset). */
    fun uniforms(source: String): Set<String> = UNIFORM.findAll(source).map { it.groupValues[1] }.filter { it != "iImage" }.toSet()

    /** `uniform shader <name>;` inputs (pictures) the shader samples. */
    fun children(source: String): Set<String> = Regex("""uniform\s+shader\s+(\w+)\s*;""").findAll(source).map { it.groupValues[1] }.toSet()

    fun idFor(fileName: String): String =
        fileName
            .substringBeforeLast('.')
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-', '_')
            .take(40)
            .ifBlank { "halo" }
            .let { if (ID.matches(it)) it else "halo" }

    /** Text checks that do not need the GPU: size, a `main`, no binary. */
    fun check(source: String) {
        if (source.isBlank()) throw ImportException("the file is empty")
        if (source.length > MAX_SOURCE)
            throw ImportException("this file is not a halo shader: it is larger than ${MAX_SOURCE / 1024} KB (a shader is a few KB of text)")
        if ('\u0000' in source) throw ImportException("not a text file")
        if (!MAIN.containsMatchIn(source)) throw ImportException("no main(float2 fragCoord) function")
    }

    /** Compiles [source] on the device; throws with the compiler's message. */
    fun compile(source: String) {
        if (!supported()) throw ImportException("halo shaders need Android 13 or newer")
        try {
            Halo.create(source)
        } catch (e: IllegalArgumentException) {
            throw ImportException(e.message?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: "shader error")
        }
    }

    /** What an import added, and which files it had to skip (name to reason). */
    class Imported(val added: List<Entry>, val skipped: List<Pair<String, String>>)

    private const val MAX_ARCHIVE = 16L * 1024 * 1024
    private const val MAX_ARCHIVE_ENTRIES = 300

    private fun readLimited(input: InputStream, limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() <= limit) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** Checks, compiles and stores one shader under a free id. [validate] is false only in tests (no GPU there). */
    private fun store(context: Context, source: String, fileName: String, taken: MutableSet<String>, validate: Boolean): Entry {
        check(source)
        if (validate) compile(source)
        val root = root(context).apply { mkdirs() }
        val files = root.listFiles { f -> f.name.endsWith(".agsl") }.orEmpty()
        var base = idFor(fileName)
        // A second file with the same name inside one archive gets a number; an earlier import of that name is replaced.
        var n = 2
        while (base in taken) base = idFor(fileName).take(36) + "-" + n++
        taken += base
        val same = files.any { it.name == "$base.agsl" }
        if (!same && files.size >= MAX_IMPORTED) throw ImportException("too many imported shaders (max $MAX_IMPORTED)")
        File(root, "$base.agsl").writeText(source)
        return Entry(IMPORTED + base, name(source, base), false)
    }

    /** Saves a shader the user chose. [validate] is false only in tests (no GPU there). */
    fun import(context: Context, input: InputStream, fileName: String, validate: Boolean = true): Entry =
        store(context, input.use { readLimited(it, MAX_SOURCE.toLong()) }.decodeToString(), fileName, HashSet(), validate)

    /**
     * Imports one `.agsl` file, or a `.zip` with many of them at once (every `.agsl` inside, in any folder). Bad files
     * are skipped and reported; the rest are added. Throws only when nothing at all could be read.
     */
    fun importAny(context: Context, input: InputStream, fileName: String, validate: Boolean = true): Imported {
        val bytes = input.use { readLimited(it, MAX_ARCHIVE) }
        if (bytes.size > MAX_ARCHIVE) throw ImportException("the file is larger than ${MAX_ARCHIVE / 1024 / 1024} MB")
        val zip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!zip) return Imported(listOf(store(context, bytes.decodeToString(), fileName, HashSet(), validate)), emptyList())
        val added = ArrayList<Entry>()
        val skipped = ArrayList<Pair<String, String>>()
        val taken = HashSet<String>()
        var seen = 0
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                if (++seen > MAX_ARCHIVE_ENTRIES) {
                    skipped += "…" to "too many files in the archive"
                    break
                }
                val name = entry.name.replace('\\', '/')
                val leaf = name.substringAfterLast('/')
                if (entry.isDirectory || name.startsWith("__MACOSX/") || leaf.startsWith(".") || !leaf.lowercase(Locale.ROOT).endsWith(".agsl")) continue
                val data = readLimited(archive, MAX_SOURCE.toLong())
                runCatching { store(context, data.decodeToString(), leaf, taken, validate) }
                    .onSuccess { added += it }
                    .onFailure { skipped += leaf to (it.message ?: it.javaClass.simpleName) }
            }
        }
        if (added.isEmpty() && skipped.isEmpty()) throw ImportException("no .agsl files found in the archive")
        return Imported(added, skipped)
    }

    fun delete(context: Context, key: String) {
        if (!key.startsWith(IMPORTED)) return
        val id = key.removePrefix(IMPORTED).takeIf { ID.matches(it) } ?: return
        File(root(context), "$id.agsl").delete()
    }

    /** Fallback when the stored halo no longer exists (a deleted shader, or Android below 13). */
    fun usable(context: Context, key: String): String =
        when {
            key == CLASSIC || key == RINGS || key == NONE -> key
            key.startsWith(PACK) ->
                ThemePacks.halo(context, key.removePrefix(PACK))
                    ?.takeIf { (it.shader != null && supported()) || it.image != null }
                    ?.let { key } ?: CLASSIC
            supported() && read(context, key) != null -> key
            else -> CLASSIC
        }

    /** The example shipped to users who want to write their own. */
    fun example(context: Context): String = read(context, BUILTIN + "celestial").orEmpty()

    /** One compiled shader and what it needs; draw with [apply]. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    class Halo private constructor(private val shader: RuntimeShader, private val names: Set<String>, private val children: Set<String>) {
        /** The shader samples a picture (`uniform shader iImage;`): a pack halo gives it the halo's own art. */
        val wantsImage
            get() = "iImage" in children

        /** Fits [bitmap] into the 240 x 240 drawing square, centred, as the shader's iImage. */
        fun setImage(bitmap: android.graphics.Bitmap) {
            val scale = 236f / maxOf(bitmap.width, bitmap.height)
            val image = android.graphics.BitmapShader(bitmap, android.graphics.Shader.TileMode.DECAL, android.graphics.Shader.TileMode.DECAL)
            image.setLocalMatrix(
                android.graphics.Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(120f - bitmap.width * scale / 2f, 120f - bitmap.height * scale / 2f)
                }
            )
            shader.setInputShader("iImage", image)
        }

        /** Sets the contract's uniforms (those the shader declares) and puts the shader on [paint]. */
        fun apply(
            paint: Paint,
            width: Float,
            height: Float,
            time: Float,
            color: Int,
            color2: Int,
            connected: Float,
            connecting: Float,
        ) {
            if ("iResolution" in names) shader.setFloatUniform("iResolution", width, height)
            if ("iTime" in names) shader.setFloatUniform("iTime", time)
            if ("iColor" in names) shader.setFloatUniform("iColor", rgba(color))
            if ("iColor2" in names) shader.setFloatUniform("iColor2", rgba(color2))
            if ("iConnected" in names) shader.setFloatUniform("iConnected", connected)
            if ("iConnecting" in names) shader.setFloatUniform("iConnecting", connecting)
            paint.shader = shader
        }

        private fun rgba(c: Int) =
            floatArrayOf(
                (c shr 16 and 0xFF) / 255f,
                (c shr 8 and 0xFF) / 255f,
                (c and 0xFF) / 255f,
                (c ushr 24) / 255f,
            )

        companion object {
            fun create(source: String) = Halo(RuntimeShader(source), uniforms(source), children(source))
        }
    }
}
