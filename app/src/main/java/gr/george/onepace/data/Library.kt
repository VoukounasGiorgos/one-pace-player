package gr.george.onepace.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SubtitleFile(
    val uri: Uri,
    val mimeType: String,
    val language: String?,
    val label: String,
)

data class Episode(
    /** Το document id του αρχείου. Σταθερό όσο δεν μετονομάζεις/μετακινείς το αρχείο. */
    val id: String,
    val uri: Uri,
    val fileName: String,
    val title: String,
    val arc: String,
    val number: Int?,
    val chapterStart: Int?,
    val subtitles: List<SubtitleFile>,
)

data class Arc(val name: String, val episodes: List<Episode>)

class Library(val arcs: List<Arc>) {
    val allEpisodes: List<Episode> = arcs.flatMap { it.episodes }
    private val index: Map<String, Int> =
        allEpisodes.withIndex().associate { (i, e) -> e.id to i }

    fun indexOf(id: String): Int = index[id] ?: -1
    fun find(id: String?): Episode? = id?.let { index[it] }?.let { allEpisodes[it] }
}

/**
 * Σαρώνει έναν φάκελο που επέλεξε ο χρήστης (Storage Access Framework).
 * Υποφάκελοι = arcs. Αν τα βίντεο είναι όλα χύμα στη ρίζα, το arc βγαίνει από το όνομα.
 */
class LibraryScanner(private val context: Context) {

    private data class Doc(val id: String, val name: String, val mime: String) {
        val isDir: Boolean get() = mime == Document.MIME_TYPE_DIR
    }

    private class Found(val doc: Doc, val folder: String?, val siblings: List<Doc>)
    private class Raw(val episode: Episode, val arcOrder: Int?)

    fun scan(tree: Uri): Library {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val found = ArrayList<Found>()
        walk(tree, rootId, null, 0, found)

        val raws = found.map { toEpisode(tree, it) }
        val episodeOrder = compareBy<Episode, Int?>(nullsLast<Int>()) { it.number }
            .thenBy(nullsLast<Int>()) { it.chapterStart }
            .thenComparator { a, b -> Naming.naturalCompare(a.fileName, b.fileName) }

        val arcs = raws.groupBy { it.episode.arc }
            .map { (name, list) ->
                val order = list.firstNotNullOfOrNull { it.arcOrder }
                    ?: list.mapNotNull { it.episode.chapterStart }.minOrNull()
                    ?: Int.MAX_VALUE
                SortableArc(order, Arc(name, list.map { it.episode }.sortedWith(episodeOrder)))
            }
            .sortedWith(
                compareBy<SortableArc> { it.order }
                    .thenComparator { a, b -> Naming.naturalCompare(a.arc.name, b.arc.name) }
            )
            .map { it.arc }

        return Library(arcs)
    }

    private class SortableArc(val order: Int, val arc: Arc)

    private fun children(tree: Uri, parentId: String): List<Doc> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
        )
        val out = ArrayList<Doc>()
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                if (id != null) out += Doc(id, c.getString(1) ?: "", c.getString(2) ?: "")
            }
        }
        return out
    }

    private fun walk(tree: Uri, parentId: String, folderName: String?, depth: Int, out: MutableList<Found>) {
        val kids = children(tree, parentId)
        for (k in kids) {
            if (k.name.startsWith(".")) continue
            if (k.isDir) {
                if (depth < 3) walk(tree, k.id, k.name, depth + 1, out)
            } else if (isVideo(k)) {
                out += Found(k, folderName, kids)
            }
        }
    }

    private fun isVideo(d: Doc): Boolean =
        Naming.ext(d.name) in Naming.VIDEO_EXT || d.mime.startsWith("video/")

    private fun toEpisode(tree: Uri, f: Found): Raw {
        val parsed = Naming.parseEpisode(f.doc.name)
        val folder = f.folder?.let { Naming.parseFolder(it) }
        val arcName = folder?.name ?: parsed.arc ?: "Επεισόδια"
        val base = Naming.base(f.doc.name)

        // Υπότιτλοι με το ίδιο όνομα: "X.srt", "X.en.srt", "X.el.ass" κλπ.
        val subs = f.siblings
            .filter { !it.isDir && Naming.ext(it.name) in Naming.SUB_MIME }
            .mapNotNull { s ->
                val sb = Naming.base(s.name)
                val lang: String? = when {
                    sb.equals(base, ignoreCase = true) -> null
                    sb.startsWith("$base.", ignoreCase = true) -> sb.substring(base.length + 1)
                    else -> return@mapNotNull null
                }
                SubtitleFile(
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, s.id),
                    mimeType = Naming.SUB_MIME.getValue(Naming.ext(s.name)),
                    language = lang,
                    label = lang?.uppercase() ?: "Υπότιτλοι",
                )
            }
            .sortedBy { it.language ?: "" }

        val episode = Episode(
            id = f.doc.id,
            uri = DocumentsContract.buildDocumentUriUsingTree(tree, f.doc.id),
            fileName = f.doc.name,
            title = parsed.title,
            arc = arcName,
            number = parsed.number,
            chapterStart = parsed.chapterStart,
            subtitles = subs,
        )
        return Raw(episode, folder?.order)
    }
}

/** Κρατάει τη βιβλιοθήκη στη μνήμη ώστε να τη μοιράζονται Home και Player. */
object LibraryRepository {
    private val _library = MutableStateFlow<Library?>(null)
    val library: StateFlow<Library?> = _library.asStateFlow()
    private val mutex = Mutex()

    suspend fun scan(context: Context, tree: Uri): Library = mutex.withLock {
        val lib = withContext(Dispatchers.IO) { LibraryScanner(context.applicationContext).scan(tree) }
        _library.value = lib
        lib
    }

    suspend fun ensureLoaded(context: Context): Library? {
        _library.value?.let { return it }
        val tree = AppSettings.treeUri(context) ?: return null
        return scan(context, tree)
    }

    fun clear() {
        _library.value = null
    }
}

object AppSettings {
    private const val PREFS = "settings"
    private const val KEY_TREE = "tree_uri"

    fun treeUri(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TREE, null)
            ?.let { Uri.parse(it) }

    fun setTreeUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TREE, uri.toString())
            .apply()
    }
}
