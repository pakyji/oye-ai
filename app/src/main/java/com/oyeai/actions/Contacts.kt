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

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$q%"),
            null
        ) ?: return null

        cursor.use {
            return if (it.moveToFirst()) it.getString(0) else null
        }
    }
}
