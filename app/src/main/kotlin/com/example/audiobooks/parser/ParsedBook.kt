package com.example.audiobooks.parser

import com.example.audiobooks.model.BookChapter
import com.example.audiobooks.model.BookSentence

data class ParsedBook(
    val title: String,
    val author: String?,
    val chapters: List<BookChapter>,
    val sentences: List<BookSentence>,
    val readerHtmlFile: String? = null
)

fun splitReadableText(text: String, chapterIndex: Int = 0): List<BookSentence> {
    val normalized = text.replace(Regex("\\s+"), " ").trim()
    if (normalized.isBlank()) return emptyList()

    val result = mutableListOf<BookSentence>()
    val current = StringBuilder()
    normalized.forEach { char ->
        current.append(char)
        val shouldSplit = char in sentenceEndChars ||
            (char in softBreakChars && current.length >= 45) ||
            current.length >= 120
        if (shouldSplit) {
            current.toString().trim().takeIf { it.length >= 2 }?.let { sentence ->
                result += BookSentence(result.size, chapterIndex, sentence)
            }
            current.clear()
        }
    }
    current.toString().trim().takeIf { it.length >= 2 }?.let { sentence ->
        result += BookSentence(result.size, chapterIndex, sentence)
    }
    return result
}

private val sentenceEndChars = setOf(
    '\u3002', '\uff01', '\uff1f', '\uff1b',
    '.', '!', '?', ';', '\n'
)

private val softBreakChars = setOf(
    '\uff0c', '\u3001', ',', ':', '\uff1a'
)
