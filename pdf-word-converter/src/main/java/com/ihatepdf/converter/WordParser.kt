package com.ihatepdf.converter

import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.xwpf.usermodel.BodyElementType
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import java.io.ByteArrayInputStream

internal object WordParser {
    fun parse(bytes: ByteArray): WordModel = when {
        bytes.startsWith(ZIP_MAGIC) -> parseDocx(bytes)
        bytes.startsWith(OLE_MAGIC) -> parseDoc(bytes)
        else -> throw ConversionException.InvalidInput("Input is not a DOCX or Word 97-2003 DOC file")
    }

    private fun parseDocx(bytes: ByteArray): WordModel =
        XWPFDocument(ByteArrayInputStream(bytes)).use { document ->
            val blocks = mutableListOf<WordBlock>()
            document.bodyElements.forEach { element ->
                when (element.elementType) {
                    BodyElementType.PARAGRAPH -> addParagraph(element as XWPFParagraph, blocks)
                    BodyElementType.TABLE -> {
                        val table = element as org.apache.poi.xwpf.usermodel.XWPFTable
                        blocks += WordBlock.Table(table.rows.map { row -> row.tableCells.map { it.text } })
                    }
                    else -> Unit
                }
            }
            WordModel(blocks)
        }

    private fun addParagraph(paragraph: XWPFParagraph, blocks: MutableList<WordBlock>) {
        val runs = paragraph.runs.mapNotNull { run ->
            val text = run.text().orEmpty()
            if (text.isEmpty()) null else WordRun(
                text = text,
                fontFamily = run.fontFamily,
                fontSizePt = run.fontSizeAsDouble?.toFloat()?.takeIf { it > 0f } ?: 11f,
                bold = run.isBold,
                italic = run.isItalic,
            )
        }
        blocks += WordBlock.Paragraph(runs)
        paragraph.runs.flatMap { it.embeddedPictures }.forEach { picture ->
            picture.pictureData?.data?.let { blocks += WordBlock.Image(it) }
        }
    }

    private fun parseDoc(bytes: ByteArray): WordModel =
        HWPFDocument(ByteArrayInputStream(bytes)).use { document ->
            val range = document.range
            val blocks = (0 until range.numParagraphs()).map { index ->
                val paragraph = range.getParagraph(index)
                val runs = (0 until paragraph.numCharacterRuns()).mapNotNull { runIndex ->
                    val run = paragraph.getCharacterRun(runIndex)
                    val text = run.text().replace("\r", "").replace("\u0007", "")
                    if (text.isEmpty()) null else WordRun(
                        text = text,
                        fontFamily = run.fontName,
                        fontSizePt = (run.fontSize / 2f).takeIf { it > 0f } ?: 11f,
                        bold = run.isBold,
                        italic = run.isItalic,
                    )
                }
                WordBlock.Paragraph(runs)
            }
            WordModel(blocks)
        }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val OLE_MAGIC = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
}
