package com.example.audiobooks.parser

import com.example.audiobooks.model.BookChapter
import com.example.audiobooks.model.BookSentence
import org.jsoup.Jsoup
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class EpubParser {
    fun parse(file: File, outputDir: File? = null): ParsedBook {
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
            val readerDir = outputDir?.let { File(it, READER_DIR).apply { mkdirs() } }
            if (readerDir != null) {
                extractPackageAssets(zip, baseDir, readerDir)
            }
            val htmlChapters = mutableListOf<String>()
            val stylesheetLinks = linkedSetOf<String>()

            spineIds.forEachIndexed { chapterIndex, idRef ->
                val item = manifest[idRef] ?: return@forEachIndexed
                val href = item.getAttribute("href").takeIf(String::isNotBlank) ?: return@forEachIndexed
                val entryPath = normalizeZipPath(baseDir + href)
                val entry = zip.getEntry(entryPath) ?: return@forEachIndexed
                val html = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { input ->
                    input.readText()
                }
                val doc = Jsoup.parse(html)
                if (readerDir != null) {
                    rewriteRelativeLinks(doc, entryPath, baseDir)
                    doc.select("link[href]").forEach { link ->
                        if (link.attr("rel").contains("stylesheet", ignoreCase = true)) {
                            stylesheetLinks += link.attr("href")
                        }
                    }
                    htmlChapters += "<section class=\"chapter\">${doc.body().html()}</section>"
                }
                val text = doc.text()
                val chapterTitle = href.substringAfterLast("/")
                    .substringBeforeLast(".")
                    .ifBlank { "Chapter ${chapterIndex + 1}" }
                val start = sentences.size
                splitReadableText(text, chapterIndex).forEach { sentence ->
                    sentences += sentence.copy(index = sentences.size)
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
            val readerHtmlFile = if (readerDir != null) {
                writeReaderHtml(readerDir, title, stylesheetLinks, htmlChapters)
                "$READER_DIR/$READER_HTML"
            } else {
                null
            }
            return ParsedBook(
                title = title,
                author = author,
                chapters = chapters,
                sentences = sentences,
                readerHtmlFile = readerHtmlFile
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

    private fun extractPackageAssets(zip: ZipFile, baseDir: String, destination: File) {
        val basePrefix = baseDir.takeIf(String::isNotBlank).orEmpty()
        zip.entries().asSequence()
            .filterNot { it.isDirectory }
            .filter { basePrefix.isBlank() || it.name.startsWith(basePrefix) }
            .forEach { entry ->
                val relative = if (basePrefix.isBlank()) entry.name else entry.name.removePrefix(basePrefix)
                val target = File(destination, relative).canonicalFile
                require(target.path.startsWith(destination.canonicalPath)) {
                    "EPUB contains an unsafe asset path."
                }
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
    }

    private fun rewriteRelativeLinks(doc: org.jsoup.nodes.Document, entryPath: String, baseDir: String) {
        val chapterDir = entryPath.substringBeforeLast("/", missingDelimiterValue = "")
        fun rewrite(value: String): String {
            if (value.isBlank() ||
                value.startsWith("#") ||
                value.startsWith("data:", ignoreCase = true) ||
                value.startsWith("http://", ignoreCase = true) ||
                value.startsWith("https://", ignoreCase = true)
            ) {
                return value
            }
            val resolved = normalizeZipPath(
                listOf(chapterDir, value.substringBefore("#"))
                    .filter(String::isNotBlank)
                    .joinToString("/")
            ).removePrefix(baseDir)
            val anchor = value.substringAfter("#", missingDelimiterValue = "")
            return if (anchor.isBlank()) resolved else "$resolved#$anchor"
        }

        doc.select("[src]").forEach { it.attr("src", rewrite(it.attr("src"))) }
        doc.select("[href]").forEach { it.attr("href", rewrite(it.attr("href"))) }
        doc.select("[xlink\\:href]").forEach { it.attr("xlink:href", rewrite(it.attr("xlink:href"))) }
    }

    private fun writeReaderHtml(
        readerDir: File,
        title: String,
        stylesheetLinks: Set<String>,
        chapters: List<String>
    ) {
        val styles = stylesheetLinks.joinToString("\n") { href ->
            "<link rel=\"stylesheet\" href=\"$href\">"
        }
        val html = """
            <!doctype html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>${title.escapeHtml()}</title>
                $styles
                <style>
                    body { margin: 0; padding: 20px; line-height: 1.7; word-break: break-word; }
                    img, svg, video { max-width: 100%; height: auto; }
                    .chapter { margin-bottom: 28px; }
                </style>
            </head>
            <body>
                ${chapters.joinToString("\n")}
            </body>
            </html>
        """.trimIndent()
        File(readerDir, READER_HTML).writeText(html, Charsets.UTF_8)
    }

    private fun String.escapeHtml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    companion object {
        private const val READER_DIR = "reader"
        private const val READER_HTML = "reader.html"
    }
}
