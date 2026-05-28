package com.example.audiobooks.parser

import com.example.audiobooks.model.BookChapter
import java.io.File

class TextParser {
    fun parse(file: File): ParsedBook {
        val text = file.readText(Charsets.UTF_8)
        val sentences = splitReadableText(text)
        require(sentences.isNotEmpty()) { "No readable text was found in this TXT file." }
        return ParsedBook(
            title = file.nameWithoutExtension,
            author = null,
            chapters = listOf(
                BookChapter(
                    index = 0,
                    title = "Text",
                    startSentenceIndex = 0,
                    endSentenceIndex = sentences.lastIndex
                )
            ),
            sentences = sentences
        )
    }
}

