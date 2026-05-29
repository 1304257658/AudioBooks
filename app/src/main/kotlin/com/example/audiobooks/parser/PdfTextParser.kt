package com.example.audiobooks.parser

import android.content.Context
import com.example.audiobooks.model.BookChapter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

class PdfTextParser(context: Context) {
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun parse(file: File): ParsedBook {
        PDDocument.load(file).use { document ->
            val outlineEntries = readOutlineEntries(document)
            if (outlineEntries.size >= 2) {
                return parseByOutline(file, document, outlineEntries)
            }
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

    private fun parseByOutline(
        file: File,
        document: PDDocument,
        outlineEntries: List<PdfOutlineEntry>
    ): ParsedBook {
        val chapters = mutableListOf<BookChapter>()
        val allSentences = mutableListOf<com.example.audiobooks.model.BookSentence>()
        outlineEntries.forEachIndexed { index, entry ->
            val nextPage = outlineEntries.getOrNull(index + 1)?.pageIndex ?: document.numberOfPages
            val startPage = (entry.pageIndex + 1).coerceIn(1, document.numberOfPages)
            val endPage = nextPage.coerceIn(startPage, document.numberOfPages)
            val text = PDFTextStripper().apply {
                this.startPage = startPage
                this.endPage = endPage
            }.getText(document)
            val start = allSentences.size
            val chapterIndex = chapters.size
            splitReadableText(text, chapterIndex).forEach { sentence ->
                allSentences += sentence.copy(index = allSentences.size)
            }
            if (allSentences.size > start) {
                chapters += BookChapter(
                    index = chapterIndex,
                    title = entry.title,
                    startSentenceIndex = start,
                    endSentenceIndex = allSentences.lastIndex
                )
            }
        }
        require(allSentences.isNotEmpty()) { "No readable text was found in this PDF. Scanned PDFs need OCR." }
        val title = document.documentInformation?.title
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: file.nameWithoutExtension
        return ParsedBook(
            title = title,
            author = document.documentInformation?.author?.trim()?.takeIf { it.isNotBlank() },
            chapters = chapters,
            sentences = allSentences
        )
    }

    private fun readOutlineEntries(document: PDDocument): List<PdfOutlineEntry> {
        val outline = document.documentCatalog.documentOutline ?: return emptyList()
        val entries = mutableListOf<PdfOutlineEntry>()

        fun visit(item: PDOutlineItem?, level: Int) {
            var current = item
            while (current != null) {
                val page = runCatching { current.findDestinationPage(document) }.getOrNull()
                val pageIndex = page?.let { document.documentCatalog.pages.indexOf(it) } ?: -1
                val title = current.title?.trim().orEmpty()
                if (title.isNotBlank() && pageIndex >= 0) {
                    entries += PdfOutlineEntry(level, title, pageIndex)
                }
                visit(current.firstChild, level + 1)
                current = current.nextSibling
            }
        }

        visit(outline.firstChild, level = 1)
        val topLevel = entries.filter { it.level == 1 }.distinctBy { it.pageIndex to it.title }
        val selected = if (topLevel.size >= 2) topLevel else entries
        return selected
            .sortedBy { it.pageIndex }
            .distinctBy { it.pageIndex to it.title }
    }

    private data class PdfOutlineEntry(
        val level: Int,
        val title: String,
        val pageIndex: Int
    )
}
