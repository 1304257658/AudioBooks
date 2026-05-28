package com.example.audiobooks.parser

import android.content.Context
import com.example.audiobooks.model.BookChapter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

class PdfTextParser(context: Context) {
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun parse(file: File): ParsedBook {
        PDDocument.load(file).use { document ->
            val chapters = mutableListOf<BookChapter>()
            val allSentences = mutableListOf<com.example.audiobooks.model.BookSentence>()
            for (page in 1..document.numberOfPages) {
                val text = PDFTextStripper().apply {
                    startPage = page
                    endPage = page
                }.getText(document)
                val start = allSentences.size
                splitReadableText(text, page - 1).forEach { sentence ->
                    allSentences += sentence.copy(index = allSentences.size)
                }
                if (allSentences.size > start) {
                    chapters += BookChapter(
                        index = chapters.size,
                        title = "Page $page",
                        startSentenceIndex = start,
                        endSentenceIndex = allSentences.lastIndex
                    )
                }
            }
            val sentences = allSentences
            require(sentences.isNotEmpty()) { "No readable text was found in this PDF. Scanned PDFs need OCR." }
            val title = document.documentInformation?.title
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension
            return ParsedBook(
                title = title,
                author = document.documentInformation?.author?.trim()?.takeIf { it.isNotBlank() },
                chapters = chapters,
                sentences = sentences
            )
        }
    }
}
