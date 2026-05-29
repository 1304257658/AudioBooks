package com.example.audiobooks.parser

import com.example.audiobooks.model.BookChapter
import java.io.File

class TextParser {
    fun parse(file: File): ParsedBook {
        val text = file.readText(Charsets.UTF_8)
        val chapterTexts = splitPlainTextChapters(text, file.nameWithoutExtension)
        val chapters = mutableListOf<BookChapter>()
        val sentences = mutableListOf<com.example.audiobooks.model.BookSentence>()
        chapterTexts.forEach { chapter ->
            val chapterIndex = chapters.size
            val start = sentences.size
            splitReadableText(chapter.text, chapterIndex).forEach { sentence ->
                sentences += sentence.copy(index = sentences.size)
            }
            if (sentences.size > start) {
                chapters += BookChapter(
                    index = chapterIndex,
                    title = chapter.title,
                    startSentenceIndex = start,
                    endSentenceIndex = sentences.lastIndex
                )
            }
        }
        require(sentences.isNotEmpty()) { "No readable text was found in this TXT file." }
        return ParsedBook(
            title = file.nameWithoutExtension,
            author = null,
            chapters = chapters.ifEmpty {
                listOf(BookChapter(0, "Text", 0, sentences.lastIndex))
            },
            sentences = sentences
        )
    }
}
