package com.bqsqwad.proedit.editor

object BqSubtitleSrt {
    fun export(captions: List<BqCaption>): String = captions.sortedBy { it.startMs }.mapIndexed { i, c ->
        (i + 1).toString() + "\n" + time(c.startMs) + " --> " + time(c.endMs) + "\n" + c.text.trim() + "\n"
    }.joinToString("\n")

    fun import(text: String): List<BqCaption> {
        val blocks = text.replace("\r", "").split("\n\n")
        return blocks.mapNotNull { block ->
            val lines = block.lines()
            if (lines.size < 3) return@mapNotNull null
            val times = lines.firstOrNull { it.contains("-->") } ?: return@mapNotNull null
            val pair = times.split("-->").map { parseTime(it.trim()) }
            if (pair.size != 2 || pair[1] <= pair[0]) return@mapNotNull null
            val body = lines.dropWhile { !it.contains("-->") }.drop(1).joinToString("\n").trim()
            if (body.isBlank()) null else BqCaption(java.util.UUID.randomUUID().toString(), body, pair[0], pair[1])
        }
    }

    private fun time(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val h = total / 3600000
        val m = (total % 3600000) / 60000
        val s = (total % 60000) / 1000
        val z = total % 1000
        return "%02d:%02d:%02d,%03d".format(h,m,s,z)
    }
    private fun parseTime(v: String): Long {
        val p = v.replace('.', ',').split(':', ',')
        if (p.size != 4) return -1
        return p[0].toLongOrNull()?.times(3600000)
            ?.plus(p[1].toLongOrNull()?.times(60000) ?: return -1)
            ?.plus(p[2].toLongOrNull()?.times(1000) ?: return -1)
            ?.plus(p[3].toLongOrNull() ?: return -1) ?: -1
    }
}