package com.example.audiobooks.storage

import android.content.Context
import android.net.Uri
import com.example.audiobooks.model.AudioBook
import com.example.audiobooks.model.BookChapter
import com.example.audiobooks.model.BookSentence
import com.example.audiobooks.parser.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BookRepository(private val context: Context) {
    private val parser = EpubParser()
    private val paths = LocalProjectPaths(context)
    private val booksDir = paths.books
    private val indexFile = paths.libraryIndex

    suspend fun loadBooks(): List<AudioBook> = withContext(Dispatchers.IO) {
        paths.ensure()
        readIndex().mapNotNull { id -> loadBook(id) }
    }

    suspend fun importEpub(uri: Uri): AudioBook = withContext(Dispatchers.IO) {
        paths.ensure()
        val id = System.currentTimeMillis().toString()
        val targetDir = File(booksDir, id).apply { mkdirs() }
        val originalName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        } ?: "imported.epub"
        val epubFile = File(targetDir, "book.epub")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open the selected EPUB file." }
            epubFile.outputStream().use { output -> input.copyTo(output) }
        }

        val parsed = parser.parse(epubFile)
        val book = AudioBook(
            id = id,
            title = parsed.title,
            author = parsed.author,
            fileName = originalName,
            importedAt = System.currentTimeMillis(),
            chapters = parsed.chapters,
            sentences = parsed.sentences
        )
        saveBook(book)
        writeIndex((readIndex() + id).distinct())
        book
    }

    suspend fun saveProgress(bookId: String, sentenceIndex: Int) = withContext(Dispatchers.IO) {
        loadBook(bookId)?.let { saveBook(it.copy(currentSentenceIndex = sentenceIndex.coerceAtLeast(0))) }
    }

    private fun loadBook(id: String): AudioBook? {
        val file = File(booksDir, "$id/book.json")
        if (!file.isFile) return null
        return runCatching { file.readText(Charsets.UTF_8).toBook() }.getOrNull()
    }

    private fun saveBook(book: AudioBook) {
        val dir = File(booksDir, book.id).apply { mkdirs() }
        File(dir, "book.json").writeText(book.toJson().toString(), Charsets.UTF_8)
    }

    private fun readIndex(): List<String> {
        if (!indexFile.isFile) return emptyList()
        val array = JSONArray(indexFile.readText(Charsets.UTF_8))
        return (0 until array.length()).map { array.getString(it) }
    }

    private fun writeIndex(ids: List<String>) {
        indexFile.writeText(JSONArray(ids).toString(), Charsets.UTF_8)
    }

    private fun AudioBook.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("author", author)
        .put("fileName", fileName)
        .put("importedAt", importedAt)
        .put("currentSentenceIndex", currentSentenceIndex)
        .put("chapters", JSONArray(chapters.map { chapter ->
            JSONObject()
                .put("index", chapter.index)
                .put("title", chapter.title)
                .put("startSentenceIndex", chapter.startSentenceIndex)
                .put("endSentenceIndex", chapter.endSentenceIndex)
        }))
        .put("sentences", JSONArray(sentences.map { sentence ->
            JSONObject()
                .put("index", sentence.index)
                .put("chapterIndex", sentence.chapterIndex)
                .put("text", sentence.text)
        }))

    private fun String.toBook(): AudioBook {
        val json = JSONObject(this)
        val chapters = json.getJSONArray("chapters").let { array ->
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                BookChapter(
                    index = item.getInt("index"),
                    title = item.getString("title"),
                    startSentenceIndex = item.getInt("startSentenceIndex"),
                    endSentenceIndex = item.getInt("endSentenceIndex")
                )
            }
        }
        val sentences = json.getJSONArray("sentences").let { array ->
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                BookSentence(
                    index = item.getInt("index"),
                    chapterIndex = item.getInt("chapterIndex"),
                    text = item.getString("text")
                )
            }
        }
        return AudioBook(
            id = json.getString("id"),
            title = json.getString("title"),
            author = json.optString("author").takeIf(String::isNotBlank),
            fileName = json.getString("fileName"),
            importedAt = json.getLong("importedAt"),
            chapters = chapters,
            sentences = sentences,
            currentSentenceIndex = json.optInt("currentSentenceIndex", 0)
        )
    }
}


