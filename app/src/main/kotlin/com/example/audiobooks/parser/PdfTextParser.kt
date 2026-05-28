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
            val text = PDFTextStripper().getText(document)
            val sentences = splitReadableText(text)
            require(sentences.isNotEmpty()) { "No readable text was found in this PDF. Scanned PDFs need OCR." }
            return ParsedBook(
                title = file.nameWithoutExtension,
                author = null,
                chapters = listOf(
                    BookChapter(
                        index = 0,
                        title = "PDF",
                        startSentenceIndex = 0,
                        endSentenceIndex = sentences.lastIndex
                    )
                ),
                sentences = sentences
            )
        }
    }
}

