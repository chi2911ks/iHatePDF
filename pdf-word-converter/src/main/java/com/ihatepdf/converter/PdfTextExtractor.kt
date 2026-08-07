package com.ihatepdf.converter

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlin.math.abs
import kotlin.math.max

internal class PdfTextExtractor(private val context: Context) {
    fun extract(uri: Uri, expectedPages: Int): List<ExtractedPageContent> {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw ConversionException.InvalidInput("Cannot reopen input Uri for text extraction")
        return input.use { stream ->
            PDDocument.load(stream).use { document ->
                if (document.numberOfPages != expectedPages) {
                    throw ConversionException.InvalidInput("PDF page count changed while reading")
                }
                (1..document.numberOfPages).map { pageNumber ->
                    val page = document.getPage(pageNumber - 1)
                    val crop = page.cropBox
                    val metadata = PageMetadata(
                        cropLeftPt = crop.lowerLeftX,
                        cropBottomPt = crop.lowerLeftY,
                        cropWidthPt = crop.width,
                        cropHeightPt = crop.height,
                        rotation = page.rotation,
                    )
                    val paragraphs = extractPage(document, pageNumber)
                    val graphicsLayer = PdfImageExtractor(page, metadata).extractGraphicsLayer()
                    ExtractedPageContent(
                        paragraphs = paragraphs,
                        images = emptyList(),
                        graphicsLayer = graphicsLayer,
                        metadata = metadata,
                        kind = when {
                            paragraphs.sumOf { it.text.length } >= 10 && graphicsLayer != null -> PdfPageKind.MIXED
                            paragraphs.sumOf { it.text.length } >= 10 -> PdfPageKind.TEXT
                            else -> PdfPageKind.SCAN
                        },
                    )
                }
            }
        }
    }

    private fun extractPage(document: PDDocument, pageNumber: Int): List<OcrParagraph> {
        val stripper = PositionStripper().apply {
            startPage = pageNumber
            endPage = pageNumber
            sortByPosition = true
        }
        stripper.getText(document)
        val pageWidth = document.getPage(pageNumber - 1).cropBox.width
        return groupLines(stripper.glyphs, pageWidth)
    }

    private fun groupLines(glyphs: List<Glyph>, pageWidth: Float): List<OcrParagraph> {
        if (glyphs.isEmpty()) return emptyList()
        val baselines = mutableListOf<MutableList<Glyph>>()
        glyphs.sortedWith(compareBy<Glyph> { it.y }.thenBy { it.x }).forEach { glyph ->
            val line = baselines.lastOrNull()
            val tolerance = max(2f, glyph.fontSizePt * 0.35f)
            if (line == null || abs(line.first().y - glyph.y) > tolerance) baselines += mutableListOf(glyph)
            else line += glyph
        }
        val segments = baselines.flatMap(::splitWideHorizontalGaps)
        val layoutLines = segments.mapNotNull { line ->
            val sorted = line.sortedBy { it.x }
            val runs = mutableListOf<StyledTextRun>()
            var previous: Glyph? = null
            sorted.forEach { glyph ->
                val gap = previous?.let { glyph.x - (it.x + it.width) } ?: 0f
                val prefix = if (previous != null && gap > max(1f, glyph.spaceWidth * 0.35f)) " " else ""
                val text = prefix + glyph.text
                val candidate = StyledTextRun(text, glyph.fontFamily, glyph.fontSizePt, glyph.bold, glyph.italic)
                val last = runs.lastOrNull()
                if (last != null && last.sameStyle(candidate)) runs[runs.lastIndex] = last.copy(text = last.text + text)
                else runs += candidate
                previous = glyph
            }
            if (runs.all { it.text.isBlank() }) null else {
                val left = sorted.minOf { it.x }
                val right = sorted.maxOf { it.x + it.width }
                val top = sorted.minOf { it.y - it.height }
                val height = sorted.maxOf { it.height }
                val paragraph = OcrParagraph(runs, left, top, height)
                LayoutLine(paragraph, left, right, top, top + height)
            }
        }
        val withTables = TableDetector.transform(layoutLines)
        return ReadingOrderResolver.order(withTables, pageWidth).map { it.paragraph }
    }

    private fun splitWideHorizontalGaps(line: List<Glyph>): List<List<Glyph>> {
        val sorted = line.sortedBy { it.x }
        if (sorted.size < 2) return listOf(sorted)
        val threshold = max(36f, sorted.maxOf { it.fontSizePt } * 3f)
        val result = mutableListOf<MutableList<Glyph>>(mutableListOf(sorted.first()))
        sorted.zipWithNext().forEach { (previous, current) ->
            val gap = current.x - (previous.x + previous.width)
            if (gap > threshold) result += mutableListOf(current) else result.last() += current
        }
        return result
    }

    private fun StyledTextRun.sameStyle(other: StyledTextRun): Boolean =
        fontFamily == other.fontFamily && fontSizePt == other.fontSizePt && bold == other.bold && italic == other.italic

    private class PositionStripper : PDFTextStripper() {
        val glyphs = mutableListOf<Glyph>()

        override fun processTextPosition(text: TextPosition) {
            val rawFont = text.font?.name?.substringAfter('+')
            val font = rawFont?.replace(Regex("[-,](Bold|Italic|Oblique|Regular).*$", RegexOption.IGNORE_CASE), "")
            val styleName = rawFont.orEmpty().lowercase()
            glyphs += Glyph(
                text = text.unicode.orEmpty(),
                x = text.xDirAdj,
                y = text.yDirAdj,
                width = text.widthDirAdj,
                height = text.heightDir,
                spaceWidth = text.widthOfSpace,
                fontFamily = font,
                fontSizePt = text.fontSizeInPt,
                bold = "bold" in styleName,
                italic = "italic" in styleName || "oblique" in styleName,
            )
        }
    }

    private data class Glyph(
        val text: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val spaceWidth: Float,
        val fontFamily: String?,
        val fontSizePt: Float,
        val bold: Boolean,
        val italic: Boolean,
    )
}

internal data class ExtractedPageContent(
    val paragraphs: List<OcrParagraph>,
    val images: List<EmbeddedImage>,
    val graphicsLayer: EmbeddedImage? = null,
    val metadata: PageMetadata? = null,
    val kind: PdfPageKind = PdfPageKind.SCAN,
)
