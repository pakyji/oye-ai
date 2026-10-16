package com.oyeai.actions

import android.content.Context
import android.provider.ContactsContract

object Contacts {

    private val phoneLike = Regex("^\\+?[0-9 ()\\-]{5,}$")

    /**
     * Returns a phone number for a contact name, or the input itself if it already looks
     * like a number. Requires READ_CONTACTS for name lookups (throws SecurityException otherwise).
     */
    fun resolveNumber(context: Context, query: String): String? {
        val q = query.trim()
        if (phoneLike.matches(q)) return q.filter { it.isDigit() || it == '+' }

        if (q.isEmpty()) return null

        // "%" and "_" typed by the user are plain characters, not wildcards.
        val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? ESCAPE '\\'",
            arrayOf("%$escaped%"),
            null
        ) ?: return null

        // Best match wins: the exact name, then names that start with it, then names that contain it.
        // Among equals, the shorter name is the closer one ("Al" finds "Al" before "Natalie").
        var best: String? = null
        var bestRank = Int.MAX_VALUE
        var bestLength = Int.MAX_VALUE
        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(0).orEmpty()
                val number = it.getString(1) ?: continue
                val rank = when {
                    name.equals(q, ignoreCase = true) -> 0
                    name.startsWith(q, ignoreCase = true) -> 1
                    else -> 2
                }
                if (rank < bestRank || (rank == bestRank && name.length < bestLength)) {
                    best = number
                    bestRank = rank
                    bestLength = name.length
                }
            }
        }
        return best
    }
}
