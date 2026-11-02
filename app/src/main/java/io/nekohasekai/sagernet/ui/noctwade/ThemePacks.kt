package io.nekohasekai.sagernet.ui.noctwade

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Theme packs: zip files with a `pack.json` that add backgrounds and characters (sprites, names and
 * dialogue). Noctwade itself ships none; the user imports them. Format: see THEME-PACKS.md.
 *
 * Import is defensive: only `pack.json` and images are extracted, paths may not leave the pack
 * folder, sizes are capped and every referenced image must decode.
 */
internal object ThemePacks {
    const val FORMAT = 1
    private const val MAX_ENTRIES = 600
    private const val MAX_TOTAL = 160L * 1024 * 1024
    private const val MAX_FILE = 25L * 1024 * 1024
    private const val MAX_JSON = 2L * 1024 * 1024
    private const val MAX_SIDE = 6000
    private const val MAX_ITEMS = 120
    private const val MAX_LINES = 60
    private const val MAX_LINE = 280
    private val ID = Regex("[a-z0-9][a-z0-9_-]{0,39}")
    private val IMAGES = setOf("webp", "png", "jpg", "jpeg")

    enum class Fit {
        /** The picture fills the width at the top and fades into the theme background. */
        TOP,
        /** The picture covers the whole screen. */
        COVER,
    }

    class Pack(
        val id: String,
        val name: String,
        val author: String,
        val dir: File,
        val backgrounds: List<Background>,
        val characters: List<PackCharacter>,
        val halos: List<PackHalo> = emptyList(),
    )

    /**
     * A ring for the connect button, not tied to any character: an AGSL [shader] (Android 13+; a flat ring that turns
     * round the button) and/or a transparent [image] (drawn behind the button, used where shaders cannot run).
     */
    class PackHalo(val pack: String, val id: String, val name: String, val image: File?, val shader: File?) {
        val ref
            get() = "$pack/$id"
    }

    class Background(
        val pack: String,
        val id: String,
        val name: String,
        val day: File,
        val night: File?,
        val fit: Fit,
    ) {
        val ref
            get() = "$pack/$id"
    }

    class Sprites(val idle: File, val blink: File?, val happy: File?, val talk: File?)

    class Lines(
        val idle: List<String>,
        val connecting: List<String>,
        val connected: List<String>,
        val stopping: List<String>,
        val tap: List<String>,
        val empty: List<String>,
    )

    /**
     * A halo floating above a character's head: a picture or an AGSL shader. [x] and [y] place its centre
     * on the sprite (0..1 across and down), [size] is its width relative to the sprite's width.
     */
    class Halo(val image: File?, val shader: File?, val x: Float, val y: Float, val size: Float)

    class PackCharacter(
        val pack: String,
        val id: String,
        val name: String,
        val subtitle: String,
        val sprites: Sprites,
        val lines: Lines,
        /** Suggested background of the same pack, or null. */
        val background: String?,
        val colors: List<Int>,
        val halo: Halo? = null,
    ) {
        val ref
            get() = "$pack/$id"
    }

    class ImportException(message: String) : Exception(message)

    fun root(context: Context) = File(context.filesDir, "theme-packs")

    @Volatile private var cache: Pair<List<String>, List<Pack>>? = null

    /** Each pack parsed once per pack.json version: a change to one pack (say a deleted entry) re-reads only that one. */
    private val parsed = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Pack>>()

    fun installed(context: Context): List<Pack> {
        val root = root(context)
        val dirs = root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }.orEmpty().sortedBy { it.name }
        val lang = Locale.getDefault().language
        val keys = dirs.map { "${it.name}@${File(it, "pack.json").lastModified()}@$lang" }
        cache?.let { (k, packs) -> if (k == keys) return packs }
        val packs =
            dirs.zip(keys).mapNotNull { (dir, key) ->
                parsed[dir.name]?.takeIf { it.first == key }?.second
                    ?: runCatching { parse(dir, dir.name) }.getOrNull()?.also { parsed[dir.name] = key to it }
            }
        parsed.keys.retainAll(dirs.map { it.name }.toSet())
        cache = keys to packs
        return packs
    }

    private fun split(ref: String): Pair<String, String>? =
        ref.split('/', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }

    fun background(context: Context, ref: String): Background? {
        val (pack, id) = split(ref) ?: return null
        return installed(context).firstOrNull { it.id == pack }?.backgrounds?.firstOrNull { it.id == id }
    }

    fun halo(context: Context, ref: String): PackHalo? {
        val (pack, id) = split(ref) ?: return null
        return installed(context).firstOrNull { it.id == pack }?.halos?.firstOrNull { it.id == id }
    }

    fun character(context: Context, ref: String): PackCharacter? {
        val (pack, id) = split(ref) ?: return null
        return installed(context).firstOrNull { it.id == pack }?.characters?.firstOrNull { it.id == id }
    }

    enum class Kind(val json: String) {
        BACKGROUNDS("backgrounds"),
        CHARACTERS("characters"),
        HALOS("halos"),
    }

    /**
     * Removes one background, character or halo from an installed pack: its entry in pack.json and the files only
     * it used. A pack with nothing left is deleted. False when the entry does not exist.
     */
    fun removeEntry(context: Context, kind: Kind, pack: String, id: String): Boolean {
        if (!ID.matches(pack) || !ID.matches(id)) return false
        val dir = File(root(context), pack)
        val file = File(dir, "pack.json")
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return false
        val all = json.optJSONArray(kind.json) ?: return false
        val kept = JSONArray()
        var removed: JSONObject? = null
        for (i in 0 until all.length()) {
            val o = all.optJSONObject(i)
            if (o != null && o.optString("id").lowercase(Locale.ROOT) == id) removed = o else kept.put(o)
        }
        val gone = removed ?: return false
        json.put(kind.json, kept)
        val still = HashSet<String>().also { files(json, it) }
        val canonical = dir.canonicalPath + File.separator
        for (path in HashSet<String>().also { files(gone, it) } - still)
            File(dir, path).takeIf { it.canonicalPath.startsWith(canonical) }?.delete()
        cache = null
        val left = Kind.values().sumOf { json.optJSONArray(it.json)?.length() ?: 0 }
        if (left == 0) dir.deleteRecursively() else file.writeText(json.toString(2))
        return true
    }

    /** File paths (strings that end in an image or .agsl extension) mentioned anywhere in [value]. */
    private fun files(value: Any?, into: MutableSet<String>) {
        when (value) {
            is String -> {
                val ext = value.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (ext in IMAGES || ext == "agsl") into += value
            }
            is JSONObject -> value.keys().forEach { files(value.opt(it), into) }
            is JSONArray -> for (i in 0 until value.length()) files(value.opt(i), into)
        }
    }

    fun delete(context: Context, id: String) {
        if (!ID.matches(id)) return
        File(root(context), id).deleteRecursively()
        cache = null
    }

    /** Extracts and checks the zip in [input], then installs it (replacing a pack with the same id). Blocking. */
    fun import(context: Context, input: InputStream): Pack {
        val root = root(context).apply { mkdirs() }
        val temp = File(root, ".import-${System.nanoTime()}").apply { mkdirs() }
        try {
            unzip(input, temp)
            val base = base(temp)
            val pack = parse(base, null)
            val target = File(root, pack.id)
            target.deleteRecursively()
            // Rename when possible; some file systems refuse to move folders, so copy then.
            if (!base.renameTo(target) && !base.copyRecursively(target, overwrite = true))
                throw ImportException("cannot install the pack")
            cache = null
            return parse(target, pack.id)
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun unzip(input: InputStream, dir: File) {
        val canonicalDir = dir.canonicalPath + File.separator
        var count = 0
        var total = 0L
        var found = false
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++count > MAX_ENTRIES) throw ImportException("too many files")
                if (entry.isDirectory) continue
                val name = entry.name.replace('\\', '/')
                val file = name.substringAfterLast('/')
                if (name.startsWith("__MACOSX/") || file.startsWith(".")) continue
                if (name.startsWith("/") || name.split('/').any { it == ".." }) throw ImportException("bad path: $name")
                val ext = file.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (file != "pack.json" && ext !in IMAGES && ext != "agsl") continue
                if (file == "pack.json") found = true
                val out = File(dir, name)
                if (!out.canonicalPath.startsWith(canonicalDir)) throw ImportException("bad path: $name")
                out.parentFile?.mkdirs()
                var size = 0L
                out.outputStream().use { o ->
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        size += n
                        total += n
                        if (size > MAX_FILE || total > MAX_TOTAL) throw ImportException("the pack is too large")
                        if (ext == "agsl" && size > HaloShaders.MAX_SOURCE) throw ImportException("shader too large: $name")
                        o.write(buffer, 0, n)
                    }
                }
            }
        }
        if (!found) throw ImportException("pack.json not found")
    }

    /** The folder with pack.json: the zip root, or its only top-level folder. */
    private fun base(dir: File): File =
        if (File(dir, "pack.json").isFile) dir
        else
            dir.listFiles()?.singleOrNull { it.isDirectory && File(it, "pack.json").isFile }
                ?: throw ImportException("pack.json not found")

    private fun parse(base: File, expectedId: String?): Pack {
        val json = File(base, "pack.json")
        if (json.length() > MAX_JSON) throw ImportException("pack.json is too large")
        val root = JSONObject(json.readText())
        val format = root.optInt("format", 1)
        if (format > FORMAT) throw ImportException("pack format $format needs a newer Noctwade")
        val id = root.optString("id").lowercase(Locale.ROOT)
        if (!ID.matches(id)) throw ImportException("invalid id \"$id\" (a-z, 0-9, - and _)")
        if (expectedId != null && expectedId != id) throw ImportException("id mismatch")
        val lang = Locale.getDefault().language
        val canonicalBase = base.canonicalPath + File.separator

        fun image(path: String?, required: Boolean, what: String): File? {
            if (path.isNullOrBlank()) {
                if (required) throw ImportException("$what is missing")
                return null
            }
            val file = File(base, path)
            if (!file.canonicalPath.startsWith(canonicalBase) || !file.isFile)
                throw ImportException("$what: file not found: $path")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ImportException("$what: not an image: $path")
            if (bounds.outWidth > MAX_SIDE || bounds.outHeight > MAX_SIDE) throw ImportException("$what: image too large: $path")
            return file
        }

        val backgrounds =
            (root.optJSONArray("backgrounds") ?: JSONArray()).objects().take(MAX_ITEMS).mapNotNull { o ->
                val bid = o.optString("id").lowercase(Locale.ROOT)
                if (!ID.matches(bid)) throw ImportException("invalid background id \"$bid\"")
                Background(
                    id,
                    bid,
                    text(o.opt("name"), lang).ifBlank { bid },
                    image(o.optString("day").ifBlank { o.optString("image") }, true, "background $bid")!!,
                    image(o.optString("night"), false, "background $bid"),
                    if (o.optString("fit") == "cover") Fit.COVER else Fit.TOP,
                )
            }
        val characters =
            (root.optJSONArray("characters") ?: JSONArray()).objects().take(MAX_ITEMS).map { o ->
                val cid = o.optString("id").lowercase(Locale.ROOT)
                if (!ID.matches(cid)) throw ImportException("invalid character id \"$cid\"")
                val sprites = o.optJSONObject("sprites") ?: throw ImportException("character $cid: sprites missing")
                val lines = pickLang(o.optJSONObject("lines"), lang)
                PackCharacter(
                    id,
                    cid,
                    text(o.opt("name"), lang).ifBlank { cid },
                    text(o.opt("subtitle"), lang),
                    Sprites(
                        image(sprites.optString("idle"), true, "character $cid idle")!!,
                        image(sprites.optString("blink"), false, "character $cid blink"),
                        image(sprites.optString("happy"), false, "character $cid happy"),
                        image(sprites.optString("talk"), false, "character $cid talk"),
                    ),
                    Lines(
                        list(lines?.opt("idle")),
                        list(lines?.opt("connecting")),
                        list(lines?.opt("connected")),
                        list(lines?.opt("stopping")),
                        list(lines?.opt("tap")),
                        list(lines?.opt("empty")),
                    ),
                    o.optString("background").lowercase(Locale.ROOT).takeIf { b -> backgrounds.any { it.id == b } },
                    list(o.opt("colors")).mapNotNull { runCatching { Color.parseColor(it) }.getOrNull() },
                    o.optJSONObject("halo")?.let { h ->
                        val shaderPath = h.optString("shader")
                        val shader =
                            shaderPath.takeIf { it.isNotBlank() }?.let { path ->
                                val f = File(base, path)
                                if (!f.canonicalPath.startsWith(canonicalBase) || !f.isFile || f.length() > HaloShaders.MAX_SOURCE)
                                    throw ImportException("character $cid halo: shader not found or too large: $path")
                                f
                            }
                        val picture = if (shader == null) image(h.optString("image"), false, "character $cid halo") else null
                        if (shader == null && picture == null) null
                        else
                            Halo(
                                picture,
                                shader,
                                h.optDouble("x", .5).toFloat().coerceIn(0f, 1f),
                                h.optDouble("y", .04).toFloat().coerceIn(-.2f, 1f),
                                h.optDouble("size", .5).toFloat().coerceIn(.1f, 1.2f),
                            )
                    },
                )
            }
        val halos =
            (root.optJSONArray("halos") ?: JSONArray()).objects().take(MAX_ITEMS).map { o ->
                val hid = o.optString("id").lowercase(Locale.ROOT)
                if (!ID.matches(hid)) throw ImportException("invalid halo id \"$hid\"")
                val shaderPath = o.optString("shader")
                val shader =
                    shaderPath.takeIf { it.isNotBlank() }?.let { path ->
                        val f = File(base, path)
                        if (!f.canonicalPath.startsWith(canonicalBase) || !f.isFile || f.length() > HaloShaders.MAX_SOURCE)
                            throw ImportException("halo $hid: shader not found or too large: $path")
                        f
                    }
                val picture = image(o.optString("image"), shader == null, "halo $hid")
                PackHalo(id, hid, text(o.opt("name"), lang).ifBlank { hid }, picture, shader)
            }
        if (backgrounds.isEmpty() && characters.isEmpty() && halos.isEmpty())
            throw ImportException("the pack has no backgrounds, characters or halos")
        return Pack(id, text(root.opt("name"), lang).ifBlank { id }, text(root.opt("author"), lang), base, backgrounds, characters, halos)
    }

    private fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }

    /** A plain string, or {"en": "...", "ru": "..."}: the device language, then English, then any. */
    private fun text(value: Any?, lang: String): String =
        when (value) {
            is String -> value
            is JSONObject -> value.optString(lang).ifBlank { value.optString("en") }.ifBlank {
                value.keys().asSequence().map { value.optString(it) }.firstOrNull { it.isNotBlank() }.orEmpty()
            }
            else -> ""
        }.trim().take(MAX_LINE)

    private fun pickLang(lines: JSONObject?, lang: String): JSONObject? {
        lines ?: return null
        return lines.optJSONObject(lang) ?: lines.optJSONObject("en") ?: lines.keys().asSequence().mapNotNull { lines.optJSONObject(it) }.firstOrNull()
    }

    private fun list(value: Any?): List<String> =
        when (value) {
            is String -> listOf(value)
            is JSONArray -> (0 until value.length()).map { value.optString(it) }
            else -> emptyList()
        }.map { it.trim().take(MAX_LINE) }.filter { it.isNotEmpty() }.take(MAX_LINES)
}
