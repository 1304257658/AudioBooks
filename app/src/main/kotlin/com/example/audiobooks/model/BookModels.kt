package com.example.audiobooks.model

data class AudioBook(
    val id: String,
    val title: String,
    val author: String?,
    val fileName: String,
    val readerHtmlFile: String? = null,
    val importedAt: Long,
    val lastReadAt: Long? = null,
    val chapters: List<BookChapter>,
    val sentences: List<BookSentence>,
    val annotations: List<BookAnnotation> = emptyList(),
    val currentSentenceIndex: Int = 0
) {
    val totalSentences: Int get() = sentences.size
}

data class BookChapter(
    val index: Int,
    val title: String,
    val startSentenceIndex: Int,
    val endSentenceIndex: Int
)

data class BookSentence(
    val index: Int,
    val chapterIndex: Int,
    val text: String
)

data class BookAnnotation(
    val id: String,
    val sentenceIndex: Int,
    val color: String,
    val note: String,
    val createdAt: Long
)


