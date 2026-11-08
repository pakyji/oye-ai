package net.kuafuai.andee.util

/**
 * Minimal markdown parser for wiki rendering.
 * Converts basic markdown to plain text with formatting hints.
 */
object Md {

    /** Convert markdown to plain text with basic formatting preserved */
    fun toPlain(md: String): String {
        return md
            .replace(Regex("^#{1,6}\\s+"), "") // headers
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1") // bold
            .replace(Regex("__(.+?)__"), "$1") // bold _
            .replace(Regex("\\*(.+?)\\*"), "$1") // italic
            .replace(Regex("_(.+?)_"), "$1") // italic _
            .replace(Regex("`(.+?)`"), "$1") // inline code
            .replace(Regex("```[\\s\\S]*?```"), "") // code blocks
            .replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1") // links
            .replace(Regex("!\\[.*?\\]\\(.+?\\)"), "") // images
            .replace(Regex("^>\\s+"), "") // blockquotes
            .replace(Regex("^[-*+]\\s+"), "• ") // list items
            .replace(Regex("^\\d+\\.\\s+"), "") // numbered lists
            .replace(Regex("\\n{3,}"), "\n\n") // collapse blank lines
            .trim()
    }

    /** Extract first heading as title */
    fun extractTitle(md: String): String? {
        val lines = md.lines()
        for (line in lines) {
            val m = Regex("^#{1,6}\\s+(.+)").matchEntire(line.trim())
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    /** Extract first paragraph as summary */
    fun extractSummary(md: String, maxLen: Int = 200): String {
        val plain = toPlain(md)
        val paragraphs = plain.split("\n\n").filter { it.isNotBlank() }
        return paragraphs.firstOrNull()?.take(maxLen) ?: ""
    }

    /** Check if markdown contains code blocks */
    fun hasCodeBlocks(md: String): Boolean = Regex("```").containsMatchIn(md)

    /** Count approximate words */
    fun wordCount(md: String): Int = toPlain(md).split(Regex("\\s+")).count { it.isNotEmpty() }
}