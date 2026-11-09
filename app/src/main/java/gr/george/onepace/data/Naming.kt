package gr.george.onepace.data

/**
 * Βοηθητικά για ονόματα αρχείων/φακέλων.
 *
 * Υποστηρίζει ονόματα τύπου One Pace, π.χ.
 *   "[One Pace][1] Romance Dawn 01 [1080p][E5A4C3D1].mp4"
 * αλλά και απλά ονόματα όπως "Romance Dawn 01.mp4" ή "05.mp4".
 */
object Naming {
    val VIDEO_EXT = setOf("mp4", "mkv", "m4v", "webm", "mov", "avi", "ts")

    /** Επέκταση υποτίτλων -> MIME type που καταλαβαίνει το Media3. */
    val SUB_MIME = mapOf(
        "srt" to "application/x-subrip",
        "vtt" to "text/vtt",
        "ass" to "text/x-ssa",
        "ssa" to "text/x-ssa",
    )

    fun ext(name: String): String = name.substringAfterLast('.', "").lowercase()
    fun base(name: String): String = if ('.' in name) name.substringBeforeLast('.') else name

    private val bracketGroups = Regex("""\[[^\]]*]|\([^)]*\)|\{[^}]*\}""")
    private val chapterGroup = Regex("""\[(\d[\d,\s\-]*)]""")
    private val firstNumber = Regex("""\d+""")
    private val trailingNumber = Regex("""^(.*?)[\s_\-]*(?<!\d)(\d{1,3})(?:v\d)?$""")
    private val leadingNumber = Regex("""^(\d{1,3})[\s._\-]+(.+)$""")
    private val spaces = Regex("""\s+""")

    data class ParsedEpisode(
        val title: String,
        val arc: String?,
        val number: Int?,
        val chapterStart: Int?,
    )

    fun parseEpisode(fileName: String): ParsedEpisode {
        val b = base(fileName)
        val chapter = chapterGroup.find(b)?.groupValues?.get(1)
            ?.let { firstNumber.find(it)?.value?.toIntOrNull() }
        val clean = b.replace(bracketGroups, " ")
            .replace('_', ' ')
            .replace('.', ' ')
            .replace(spaces, " ")
            .trim()
        val m = trailingNumber.find(clean)
        if (m != null) {
            val arc = m.groupValues[1].trim().trimEnd('-', ' ')
            return ParsedEpisode(
                title = clean,
                arc = arc.ifBlank { null },
                number = m.groupValues[2].toIntOrNull(),
                chapterStart = chapter,
            )
        }
        return ParsedEpisode(clean.ifBlank { b }, null, null, chapter)
    }

    data class ParsedFolder(val name: String, val order: Int?)

    /** "01 Romance Dawn" -> ("Romance Dawn", 1) */
    fun parseFolder(folder: String): ParsedFolder {
        val clean = folder.replace(bracketGroups, " ")
            .replace('_', ' ')
            .replace(spaces, " ")
            .trim()
        val m = leadingNumber.find(clean)
        return if (m != null) {
            ParsedFolder(m.groupValues[2].trim(), m.groupValues[1].toInt())
        } else {
            ParsedFolder(clean.ifBlank { folder }, null)
        }
    }

    /** Φυσική ταξινόμηση: "Ep 2" < "Ep 10". */
    fun naturalCompare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ei = i
                while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j
                while (ej < b.length && b[ej].isDigit()) ej++
                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
                i = ei
                j = ej
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
