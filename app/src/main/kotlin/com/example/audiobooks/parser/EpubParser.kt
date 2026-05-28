package com.example.audiobooks.parser

import com.example.audiobooks.model.BookChapter
import com.example.audiobooks.model.BookSentence
import org.jsoup.Jsoup
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

data class ParsedEpub(
    val title: String,
    val author: String?,
    val chapters: List<BookChapter>,
    val sentences: List<BookSentence>
)

class EpubParser {
    fun parse(file: File): ParsedEpub {
        ZipFile(file).use { zip ->
            val opfPath = findOpfPath(zip)
            val opf = parseXml(zip.readText(opfPath))
            val packageDir = opfPath.substringBeforeLast("/", missingDelimiterValue = "")
            val baseDir = if (packageDir.isBlank()) "" else "$packageDir/"

            val title = opf.textOfFirst("dc:title").ifBlank { file.nameWithoutExtension }
            val author = opf.textOfFirst("dc:creator").ifBlank { null }
            val manifest = opf.elements("item").associateBy { it.getAttribute("id") }
            val spineIds = opf.elements("itemref")
                .mapNotNull { it.getAttribute("idref").takeIf(String::isNotBlank) }

            val chapters = mutableListOf<BookChapter>()
            val sentences = mutableListOf<BookSentence>()

            spineIds.forEachIndexed { chapterIndex, idRef ->
                val item = manifest[idRef] ?: return@forEachIndexed
                val href = item.getAttribute("href").takeIf(String::isNotBlank) ?: return@forEachIndexed
                val entryPath = normalizeZipPath(baseDir + href)
                val entry = zip.getEntry(entryPath) ?: return@forEachIndexed
                val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { input ->
                    Jsoup.parse(input.readText()).text()
                }
                val chapterTitle = href.substringAfterLast("/")
                    .substringBeforeLast(".")
                    .ifBlank { "Chapter ${chapterIndex + 1}" }
                val start = sentences.size
                splitSentences(text).forEach { sentenceText ->
                    sentences += BookSentence(
                        index = sentences.size,
                        chapterIndex = chapterIndex,
                        text = sentenceText
                    )
                }
                if (sentences.size > start) {
                    chapters += BookChapter(
                        index = chapterIndex,
                        title = chapterTitle,
                        startSentenceIndex = start,
                        endSentenceIndex = sentences.lastIndex
                    )
                }
            }

            require(sentences.isNotEmpty()) { "No readable text was found in this EPUB." }
            return ParsedEpub(
                title = title,
                author = author,
                chapters = chapters,
                sentences = sentences
            )
        }
    }

    private fun findOpfPath(zip: ZipFile): String {
        val container = zip.readText("META-INF/container.xml")
        val xml = parseXml(container)
        val rootFile = xml.elements("rootfile").firstOrNull()
        return rootFile?.getAttribute("full-path")?.takeIf(String::isNotBlank)
            ?: error("EPUB container does not point to an OPF file.")
    }

    private fun splitSentences(text: String): List<String> {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return emptyList()

        val result = mutableListOf<String>()
        val current = StringBuilder()
        normalized.forEach { char ->
            current.append(char)
            val shouldSplit = char in sentenceEndChars ||
                (char in softBreakChars && current.length >= 45) ||
                current.length >= 120
            if (shouldSplit) {
                current.toString().trim().takeIf { it.length >= 2 }?.let(result::add)
                current.clear()
            }
        }
        current.toString().trim().takeIf { it.length >= 2 }?.let(result::add)
        return result
    }

    private fun ZipFile.readText(path: String): String {
        val entry = getEntry(path) ?: error("EPUB entry missing: $path")
        return getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun parseXml(text: String): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val doc = factory.newDocumentBuilder().parse(text.byteInputStream())
        return doc.documentElement
    }

    private fun Element.elements(name: String): List<Element> {
        val nodes = getElementsByTagName(name)
        if (nodes.length == 0 && name.contains(":")) {
            return elements(name.substringAfter(":"))
        }
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun Element.textOfFirst(name: String): String =
        elements(name).firstOrNull()?.textContent?.trim().orEmpty()

    private fun normalizeZipPath(path: String): String =
        path.replace("\\", "/")
            .split("/")
            .fold(mutableListOf<String>()) { acc, part ->
                when (part) {
                    "", "." -> acc
                    ".." -> if (acc.isNotEmpty()) acc.also { it.removeAt(it.lastIndex) } else acc
                    else -> acc.also { it += part }
                }
            }
            .joinToString("/")

    private companion object {
        val sentenceEndChars = setOf(
            '\u3002', '\uff01', '\uff1f', '\uff1b',
            '.', '!', '?', ';', '\n'
        )
        val softBreakChars = setOf(
            '\uff0c', '\u3001', ',', ':', '\uff1a'
        )
    }
}
