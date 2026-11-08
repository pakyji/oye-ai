package net.kuafuai.andee.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream

/**
 * Wiki body storage on encrypted app private storage.
 * Index lives in Notebook.wiki (Map<String, WikiArticle> with title/secret flag only).
 * Bodies are .md files named by WikiIndexEntry.filename (uuid.md).
 */
class LocalWikiStore(private val context: Context) {

    private val wikiDir: File = File(context.filesDir, "wiki").apply { mkdirs() }

    fun readBody(filename: String): String? {
        val file = File(wikiDir, filename)
        return if (file.exists()) {
            FileInputStream(file).use { it.readBytes().decodeToString() }
        } else null
    }

    fun writeBody(filename: String, body: String) {
        val file = File(wikiDir, filename)
        FileOutputStream(file).use { it.write(body.encodeToByteArray()) }
    }

    fun deleteBody(filename: String) {
        File(wikiDir, filename).delete()
    }

    fun listFiles(): List<String> = wikiDir.listFiles()?.map { it.name } ?: emptyList()

    fun generateFilename(): String = java.util.UUID.randomUUID().toString() + ".md"
}