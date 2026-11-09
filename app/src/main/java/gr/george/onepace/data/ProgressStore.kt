package gr.george.onepace.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class Progress(
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val watched: Boolean,
) {
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Έχει ξεκινήσει αλλά δεν έχει τελειώσει. */
    val inProgress: Boolean
        get() = !watched && positionMs > 5_000 && fraction < ProgressStore.WATCHED_RATIO

    /** Από πού να συνεχίσει (λίγα δευτερόλεπτα πίσω για να θυμηθείς πού ήσουν). */
    val resumePositionMs: Long
        get() = if (positionMs > 5_000 && fraction < ProgressStore.WATCHED_RATIO) {
            (positionMs - 3_000).coerceAtLeast(0)
        } else {
            0L
        }
}

/** Αποθηκεύει τοπικά (SharedPreferences) πού έχεις μείνει σε κάθε επεισόδιο. */
object ProgressStore {
    const val WATCHED_RATIO = 0.92f

    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow<Map<String, Progress>>(emptyMap())
    val state: StateFlow<Map<String, Progress>> = _state.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("progress", Context.MODE_PRIVATE)
        prefs = p
        val loaded = HashMap<String, Progress>()
        for ((key, value) in p.all) {
            val decoded = (value as? String)?.let { decode(it) }
            if (decoded != null) loaded[key] = decoded
        }
        _state.value = loaded
    }

    fun get(id: String): Progress? = _state.value[id]

    fun save(id: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0 || positionMs < 1_000) return
        val old = _state.value[id]
        val finished = positionMs >= durationMs * WATCHED_RATIO
        putAll(
            mapOf(
                id to Progress(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = System.currentTimeMillis(),
                    watched = finished || old?.watched == true,
                )
            )
        )
    }

    fun markWatched(ids: Collection<String>) {
        val now = System.currentTimeMillis()
        val current = _state.value
        putAll(ids.associateWith { id ->
            val old = current[id]
            val dur = old?.durationMs ?: 0L
            Progress(dur, dur, now, true)
        })
    }

    fun markUnwatched(id: String) {
        val old = _state.value[id]
        putAll(mapOf(id to Progress(0L, old?.durationMs ?: 0L, old?.updatedAt ?: 0L, false)))
    }

    private fun putAll(entries: Map<String, Progress>) {
        if (entries.isEmpty()) return
        _state.update { it + entries }
        val editor = prefs?.edit() ?: return
        for ((k, v) in entries) editor.putString(k, encode(v))
        editor.apply()
    }

    private fun encode(p: Progress): String =
        "${p.positionMs};${p.durationMs};${p.updatedAt};${if (p.watched) 1 else 0}"

    private fun decode(s: String): Progress? {
        val parts = s.split(';')
        if (parts.size != 4) return null
        return Progress(
            positionMs = parts[0].toLongOrNull() ?: return null,
            durationMs = parts[1].toLongOrNull() ?: return null,
            updatedAt = parts[2].toLongOrNull() ?: return null,
            watched = parts[3] == "1",
        )
    }
}

/** Τι να δείξει η μεγάλη κάρτα «Συνέχεια» στην αρχική. */
data class ContinueTarget(val episode: Episode, val label: String)

fun continueTarget(library: Library, progress: Map<String, Progress>): ContinueTarget? {
    val all = library.allEpisodes
    if (all.isEmpty()) return null

    val last = progress.entries
        .filter { it.value.updatedAt > 0 && library.find(it.key) != null }
        .maxByOrNull { it.value.updatedAt }
        ?: return ContinueTarget(all.first(), "Ξεκίνα από την αρχή")

    val ep = library.find(last.key) ?: return ContinueTarget(all.first(), "Ξεκίνα από την αρχή")
    val p = last.value
    if (!p.watched && p.fraction < ProgressStore.WATCHED_RATIO) return ContinueTarget(ep, "Συνέχεια")

    val idx = library.indexOf(ep.id)
    val next = all.drop(idx + 1).firstOrNull { progress[it.id]?.watched != true }
    return if (next != null) {
        ContinueTarget(next, "Επόμενο επεισόδιο")
    } else {
        ContinueTarget(ep, "Τα είδες όλα — ξαναδές το")
    }
}
