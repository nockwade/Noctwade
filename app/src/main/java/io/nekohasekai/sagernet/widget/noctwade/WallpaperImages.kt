package io.nekohasekai.sagernet.widget.noctwade

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Size-bounded shared decoding. Grid previews never decode phone-sized bitmaps. */
internal object WallpaperImages {
    private val main = Handler(Looper.getMainLooper())
    private val workers =
        Executors.newFixedThreadPool(2) { job ->
            Thread(job, "noctwade-wallpaper").apply { isDaemon = true }
        }
    private val cache =
        object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
        }

    internal fun sampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        val w = targetWidth.coerceAtLeast(1)
        val h = targetHeight.coerceAtLeast(1)
        var sample = 1
        while (width / (sample * 2) >= w && height / (sample * 2) >= h && sample < 128) sample *= 2
        return sample
    }

    fun load(
        resources: Resources,
        id: Int,
        width: Int,
        height: Int,
        preview: Boolean,
        deliver: (Bitmap?) -> Unit,
    ): Future<*> =
        workers.submit {
            val bitmap =
                runCatching {
                        val bounds =
                            BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                                inScaled = false
                            }
                        BitmapFactory.decodeResource(resources, id, bounds)
                        val targetW = if (preview) minOf(width, 192) else width
                        val targetH = if (preview) minOf(height, 192) else height
                        val sample = sampleSize(bounds.outWidth, bounds.outHeight, targetW, targetH)
                        val key = "$id:$sample"
                        cache.get(key)
                            ?: BitmapFactory.decodeResource(
                                    resources,
                                    id,
                                    BitmapFactory.Options().apply {
                                        inScaled = false
                                        inSampleSize = sample
                                        inPreferredConfig = Bitmap.Config.ARGB_8888
                                    },
                                )
                                ?.also { cache.put(key, it) }
                    }
                    .getOrNull()
            if (!Thread.currentThread().isInterrupted) main.post { deliver(bitmap) }
        }
}
