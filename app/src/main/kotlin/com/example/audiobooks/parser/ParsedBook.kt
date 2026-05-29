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

data class ParsedChapterText(
    val title: String,
    val text: String
)

fun splitReadableText(text: String, chapterIndex: Int = 0): List<BookSentence> {
    val paragraphs = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split(Regex("\\n\\s*\\n+|(?<=。|！|？|\\.|!|\\?)\\s*\\n+"))
        .map { it.replace(Regex("[\\t\\x0B\\f ]+"), " ").trim() }
        .filter { it.isNotBlank() }
    if (paragraphs.isEmpty()) return emptyList()

    val result = mutableListOf<BookSentence>()
    paragraphs.forEachIndexed { paragraphIndex, paragraph ->
        val pieces = splitParagraph(paragraph)
        pieces.forEachIndexed { pieceIndex, piece ->
            val textWithMarker = if (paragraphIndex < paragraphs.lastIndex && pieceIndex == pieces.lastIndex) {
                piece + PARAGRAPH_BREAK_MARKER
            } else {
                piece
            }
            result += BookSentence(result.size, chapterIndex, textWithMarker)
        }
    }
    return result
}

private fun splitParagraph(paragraph: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    while (index < paragraph.length) {
        val char = paragraph[index]
        current.append(char)

        var shouldSplit = false
        if (char in sentenceEndChars) {
            while (index + 1 < paragraph.length && paragraph[index + 1] in closingQuoteChars) {
                index += 1
                current.append(paragraph[index])
            }
            shouldSplit = current.length >= MIN_SENTENCE_CHARS
        } else if (char in softBreakChars && current.length >= SOFT_BREAK_CHARS) {
            shouldSplit = true
        } else if (current.length >= HARD_BREAK_CHARS && char !in openingQuoteChars) {
            shouldSplit = true
        }

        if (shouldSplit) {
            appendReadablePiece(result, current)
        }
        index += 1
    }
    appendReadablePiece(result, current)
    return result
}

private fun appendReadablePiece(result: MutableList<String>, current: StringBuilder) {
    current.toString()
        .replace(Regex("\\s+"), " ")
        .trim()
        .takeIf { it.length >= 2 }
        ?.let { result += it }
    current.clear()
}

private val sentenceEndChars = setOf(
    '\u3002', '\uff01', '\uff1f', '\uff1b',
    '.', '!', '?', ';'
)

private val softBreakChars = setOf(
    '\uff0c', '\u3001', ',', ':', '\uff1a'
)

private val closingQuoteChars = setOf(
    '"', '\'', ')', ']', '}', '\u201d', '\u2019', '\u300d', '\u300f', '\u300b', '\u3011'
)

private val openingQuoteChars = setOf(
    '"', '\'', '(', '[', '{', '\u201c', '\u2018', '\u300c', '\u300e', '\u300a', '\u3010'
)

private const val MIN_SENTENCE_CHARS = 4
private const val SOFT_BREAK_CHARS = 56
private const val HARD_BREAK_CHARS = 150
private const val PARAGRAPH_BREAK_MARKER = "\u2029"

fun splitPlainTextChapters(text: String, defaultTitle: String): List<ParsedChapterText> {
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
    val lines = normalized.lines()
    val headings = detectPlainTextHeadings(lines)
    if (headings.size < 2) {
        return listOf(ParsedChapterText(defaultTitle, normalized))
    }

    val result = mutableListOf<ParsedChapterText>()
    if (headings.first().lineIndex > 0) {
        lines.subList(0, headings.first().lineIndex)
            .joinToString("\n")
            .trim()
            .takeIf(String::isNotBlank)
            ?.let { result += ParsedChapterText("Preface", it) }
    }
    headings.forEachIndexed { index, heading ->
        val start = heading.lineIndex + 1
        val end = headings.getOrNull(index + 1)?.lineIndex ?: lines.size
        val body = lines.subList(start, end).joinToString("\n").trim()
        val title = heading.title.ifBlank { "Chapter ${result.size + 1}" }
        if (body.isNotBlank()) {
            result += ParsedChapterText(title, body)
        }
    }
    return result.ifEmpty { listOf(ParsedChapterText(defaultTitle, normalized)) }
}

private fun detectPlainTextHeadings(lines: List<String>): List<PlainTextHeading> {
    val headings = mutableListOf<PlainTextHeading>()
    lines.forEachIndexed { index, line ->
        val trimmed = line.trim()
        val previousBlank = index == 0 || lines[index - 1].isBlank()
        val nextBlank = index == lines.lastIndex || lines[index + 1].isBlank()
        if (trimmed.length in 1..90 && isMarkdownHeading(trimmed)) {
            headings += PlainTextHeading(index, trimmed.trimStart('#').trim())
        } else if (trimmed.length in 2..90 && previousBlank && nextBlank && isTextChapterHeading(trimmed)) {
            headings += PlainTextHeading(index, trimmed)
        } else if (index + 1 < lines.size && previousBlank && isSetextHeadingUnderline(lines[index + 1].trim())) {
            val title = trimmed.takeIf { it.length in 2..90 && !isSeparatorLine(it) }
            if (title != null) headings += PlainTextHeading(index, title)
        }
    }
    return headings.distinctBy { it.lineIndex }
}

private fun isMarkdownHeading(line: String): Boolean =
    Regex("^#{1,6}\\s+\\S.{0,80}$").matches(line)

private fun isSetextHeadingUnderline(line: String): Boolean =
    Regex("^(=+|-{3,})$").matches(line)

private fun isTextChapterHeading(line: String): Boolean {
    val patterns = listOf(
        Regex("^第[零〇一二三四五六七八九十百千万0-9]+[章节回卷部篇](\\s+|[:：、-])?.{0,50}$"),
        Regex("^(Chapter|CHAPTER)\\s+([0-9]+|[IVXLCDM]+|[A-Z]+)(\\s+|[:：.-])?.{0,50}$"),
        Regex("^[0-9]{1,3}[.、]\\s+\\S.{0,60}$")
    )
    return patterns.any { it.matches(line) } || isAllCapsHeading(line)
}

private fun isAllCapsHeading(line: String): Boolean {
    val letters = line.filter(Char::isLetter)
    return letters.length >= 4 && letters.all(Char::isUpperCase) && line.length <= 60
}

private fun isSeparatorLine(line: String): Boolean =
    Regex("^([*=_-])\\1{2,}$").matches(line)

private data class PlainTextHeading(
    val lineIndex: Int,
    val title: String
)
