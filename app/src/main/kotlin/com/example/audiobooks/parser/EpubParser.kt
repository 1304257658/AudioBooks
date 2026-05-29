package com.example.audiobooks.parser

import com.example.audiobooks.model.BookChapter
import com.example.audiobooks.model.BookSentence
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
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
            val spine = opf.elements("itemref")
                .filterNot { it.getAttribute("linear").equals("no", ignoreCase = true) }
                .mapNotNull { item ->
                    item.getAttribute("idref")
                        .takeIf(String::isNotBlank)
                        ?.let { SpineEntry(it) }
                }
            val tocEntries = parseNavigationEntries(zip, opf, manifest, baseDir)

            val chapters = mutableListOf<BookChapter>()
            val sentences = mutableListOf<BookSentence>()
            val readerDir = outputDir?.let { File(it, READER_DIR).apply { mkdirs() } }
            if (readerDir != null) {
                extractPackageAssets(zip, baseDir, readerDir)
            }
            val htmlChapters = mutableListOf<String>()
            val stylesheetLinks = linkedSetOf<String>()

            spine.forEach { spineEntry ->
                val item = manifest[spineEntry.idRef] ?: return@forEach
                val href = item.getAttribute("href").takeIf(String::isNotBlank) ?: return@forEach
                val entryPath = normalizeZipPath(baseDir + href)
                val entry = zip.getEntry(entryPath) ?: return@forEach
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
                val fallbackTitle = doc.select("h1, h2, h3, title")
                    .firstOrNull()
                    ?.text()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: href.substringAfterLast("/")
                        .substringBeforeLast(".")
                        .ifBlank { "Chapter ${chapters.size + 1}" }
                val sections = splitEpubDocument(
                    doc = doc,
                    entryPath = entryPath,
                    tocEntries = tocEntries.filter { it.path == entryPath },
                    fallbackTitle = fallbackTitle
                )
                sections.forEach { section ->
                    val start = sentences.size
                    val chapterIndex = chapters.size
                    splitReadableText(section.text, chapterIndex).forEach { sentence ->
                        sentences += sentence.copy(index = sentences.size)
                    }
                    if (sentences.size > start) {
                        chapters += BookChapter(
                            index = chapterIndex,
                            title = section.title,
                            startSentenceIndex = start,
                            endSentenceIndex = sentences.lastIndex
                        )
                    }
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

    private fun parseNavigationEntries(
        zip: ZipFile,
        opf: Element,
        manifest: Map<String, Element>,
        baseDir: String
    ): List<TocEntry> {
        val navEntries = parseEpub3NavEntries(zip, manifest, baseDir)
        if (navEntries.isNotEmpty()) return navEntries
        return parseNcxEntries(zip, opf, manifest, baseDir)
    }

    private fun parseEpub3NavEntries(
        zip: ZipFile,
        manifest: Map<String, Element>,
        baseDir: String
    ): List<TocEntry> {
        val navItem = manifest.values.firstOrNull {
            it.getAttribute("properties").split(Regex("\\s+")).any { property -> property == "nav" }
        } ?: return emptyList()
        val href = navItem.getAttribute("href").takeIf(String::isNotBlank) ?: return emptyList()
        val navPath = normalizeZipPath(baseDir + href)
        val entry = zip.getEntry(navPath) ?: return emptyList()
        val html = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val doc = Jsoup.parse(html)
        val toc = doc.select("nav").firstOrNull { nav ->
            nav.attr("epub:type").split(Regex("\\s+")).contains("toc") ||
                nav.attr("type").equals("toc", ignoreCase = true)
        } ?: return emptyList()
        val navDir = navPath.substringBeforeLast("/", missingDelimiterValue = "")
        return toc.select("a[href]").mapNotNull { link ->
            val title = link.text().trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
            val target = resolveEpubHref(navDir, baseDir, link.attr("href"))
            target?.let { TocEntry(title = title, path = it.first, fragment = it.second) }
        }.distinctBy { it.path to it.fragment }
    }

    private fun parseNcxEntries(
        zip: ZipFile,
        opf: Element,
        manifest: Map<String, Element>,
        baseDir: String
    ): List<TocEntry> {
        val tocId = opf.elements("spine").firstOrNull()?.getAttribute("toc")?.takeIf(String::isNotBlank)
            ?: manifest.values.firstOrNull {
                it.getAttribute("media-type").equals("application/x-dtbncx+xml", ignoreCase = true)
            }?.getAttribute("id")
            ?: return emptyList()
        val ncxItem = manifest[tocId] ?: return emptyList()
        val href = ncxItem.getAttribute("href").takeIf(String::isNotBlank) ?: return emptyList()
        val ncxPath = normalizeZipPath(baseDir + href)
        val entry = zip.getEntry(ncxPath) ?: return emptyList()
        val xml = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val ncx = parseXml(xml)
        val ncxDir = ncxPath.substringBeforeLast("/", missingDelimiterValue = "")
        return ncx.elements("navPoint").mapNotNull { point ->
            val label = point.elements("text").firstOrNull()?.textContent?.trim()?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val src = point.elements("content").firstOrNull()?.getAttribute("src")?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            resolveEpubHref(ncxDir, baseDir, src)?.let {
                TocEntry(title = label, path = it.first, fragment = it.second)
            }
        }.distinctBy { it.path to it.fragment }
    }

    private fun resolveEpubHref(fromDir: String, baseDir: String, href: String): Pair<String, String?>? {
        if (href.isBlank() ||
            href.startsWith("#") ||
            href.startsWith("http://", ignoreCase = true) ||
            href.startsWith("https://", ignoreCase = true) ||
            href.startsWith("data:", ignoreCase = true)
        ) {
            return null
        }
        val path = normalizeZipPath(
            listOf(fromDir, href.substringBefore("#"))
                .filter(String::isNotBlank)
                .joinToString("/")
        ).takeIf(String::isNotBlank) ?: return null
        val normalizedPath = if (baseDir.isNotBlank() && !path.startsWith(baseDir)) "$baseDir$path" else path
        val fragment = href.substringAfter("#", missingDelimiterValue = "").takeIf(String::isNotBlank)
        return normalizedPath to fragment
    }

    private fun splitEpubDocument(
        doc: org.jsoup.nodes.Document,
        entryPath: String,
        tocEntries: List<TocEntry>,
        fallbackTitle: String
    ): List<ParsedChapterText> {
        if (tocEntries.isEmpty()) {
            val headingSections = splitByHeadings(doc)
            return headingSections.ifEmpty { listOf(ParsedChapterText(fallbackTitle, doc.text())) }
        }

        val fileStartTitle = tocEntries.firstOrNull { it.fragment == null }?.title ?: tocEntries.first().title
        val titleByFragment = tocEntries
            .mapNotNull { entry -> entry.fragment?.let { it to entry.title } }
            .toMap()
        if (titleByFragment.isEmpty()) {
            return listOf(ParsedChapterText(fileStartTitle, doc.text()))
        }

        val sections = mutableListOf<ParsedChapterText>()
        var currentTitle = fileStartTitle
        val current = StringBuilder()
        var sawTocAnchor = false

        fun flush() {
            current.toString()
                .replace(Regex("[ \\t\\x0B\\f]+"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
                .takeIf(String::isNotBlank)
                ?.let { sections += ParsedChapterText(currentTitle, it) }
            current.clear()
        }

        fun visit(node: org.jsoup.nodes.Node) {
            if (node is org.jsoup.nodes.Element) {
                val anchorTitle = anchorKeys(node).firstNotNullOfOrNull { key -> titleByFragment[key] }
                if (anchorTitle != null) {
                    if (current.isNotBlank()) flush()
                    currentTitle = anchorTitle
                    sawTocAnchor = true
                }
                if (node.tagName() in blockBeforeTags && current.isNotBlank()) current.append('\n')
            }
            if (node is TextNode) {
                val text = node.text().replace(Regex("\\s+"), " ").trim()
                if (text.isNotBlank()) {
                    if (current.isNotEmpty() && !current.last().isWhitespace()) current.append(' ')
                    current.append(text)
                }
            }
            node.childNodes().forEach(::visit)
            if (node is org.jsoup.nodes.Element && node.tagName() in blockAfterTags && current.isNotBlank()) {
                current.append('\n')
            }
        }

        doc.body()?.childNodes()?.forEach(::visit)
        flush()

        if (!sawTocAnchor) {
            return splitByHeadings(doc).ifEmpty {
                listOf(ParsedChapterText(fileStartTitle.ifBlank { entryPath }, doc.text()))
            }
        }
        return sections
    }

    private fun splitByHeadings(doc: org.jsoup.nodes.Document): List<ParsedChapterText> {
        val headings = doc.select("h1, h2")
            .filter { heading -> heading.text().trim().length in 2..90 }
        if (headings.size < 2) return emptyList()

        val sections = mutableListOf<ParsedChapterText>()
        var currentTitle = headings.first().text().trim()
        val current = StringBuilder()

        fun flush() {
            current.toString().trim().takeIf(String::isNotBlank)?.let {
                sections += ParsedChapterText(currentTitle, it)
            }
            current.clear()
        }

        fun visit(node: org.jsoup.nodes.Node) {
            if (node is org.jsoup.nodes.Element && node.`is`("h1, h2")) {
                if (current.isNotBlank()) flush()
                currentTitle = node.text().trim().ifBlank { currentTitle }
            }
            if (node is TextNode) {
                val text = node.text().replace(Regex("\\s+"), " ").trim()
                if (text.isNotBlank()) {
                    if (current.isNotEmpty() && !current.last().isWhitespace()) current.append(' ')
                    current.append(text)
                }
            }
            node.childNodes().forEach(::visit)
            if (node is org.jsoup.nodes.Element && node.tagName() in blockAfterTags && current.isNotBlank()) {
                current.append('\n')
            }
        }

        doc.body()?.childNodes()?.forEach(::visit)
        flush()
        return sections
    }

    private fun anchorKeys(element: org.jsoup.nodes.Element): List<String> =
        listOf(
            element.id(),
            element.attr("name"),
            element.attr("xml:id")
        ).filter(String::isNotBlank)

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

    private data class SpineEntry(val idRef: String)

    private data class TocEntry(
        val title: String,
        val path: String,
        val fragment: String?
    )

    companion object {
        private const val READER_DIR = "reader"
        private const val READER_HTML = "reader.html"
        private val blockBeforeTags = setOf("h1", "h2", "h3", "p", "div", "section", "article", "li", "blockquote")
        private val blockAfterTags = blockBeforeTags + setOf("br")
    }
}
