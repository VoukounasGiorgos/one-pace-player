package gr.george.onepace.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Βγάζει ένα καρέ από κάθε επεισόδιο για εξώφυλλο και το κρατάει στην cache,
 * ώστε να φτιάχνεται μόνο την πρώτη φορά.
 */
object Thumbnails {
    private const val WIDTH = 480
    private val memory = LruCache<String, ImageBitmap>(120)
    private val limiter = Semaphore(2)

    fun cached(id: String): ImageBitmap? = memory.get(id)

    suspend fun load(context: Context, episode: Episode): ImageBitmap? {
        memory.get(episode.id)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(File(context.cacheDir, "thumbs"), sha1(episode.id) + ".jpg")
            val bitmap = if (file.exists()) {
                BitmapFactory.decodeFile(file.path)
            } else {
                limiter.withPermit { generate(context, episode.uri, file) }
            }
            bitmap?.asImageBitmap()?.also { memory.put(episode.id, it) }
        }
    }

    private fun generate(context: Context, uri: Uri, out: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            // ~20% μέσα στο επεισόδιο (μέχρι 2 λεπτά) για να μην πέσει σε μαύρο καρέ
            val atMs = if (durationMs > 0) minOf((durationMs * 0.2).toLong(), 120_000L) else 10_000L
            val frame = retriever.getFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val height = (frame.height * WIDTH / frame.width.toFloat()).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(frame, WIDTH, height, true)
            if (scaled !== frame) frame.recycle()
            out.parentFile?.mkdirs()
            FileOutputStream(out).use { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            return scaled
        } catch (e: Exception) {
            return null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
