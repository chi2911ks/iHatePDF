package com.ihatepdf.converter

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.Document
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz
import java.math.BigInteger
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.STRelFromH
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.STRelFromV

internal object PoiDocxWriter {
    fun write(pages: List<EditablePage>, output: OutputStream) {
        require(pages.isNotEmpty()) { "At least one page is required" }
        XWPFDocument().use { document ->
            pages.forEachIndexed { pageIndex, page ->
                if (pageIndex > 0) document.createParagraph().createRun().addBreak(BreakType.PAGE)
                writePage(document, page)
            }
            configureLastSection(document, pages.last())
            document.write(output)
        }
    }

    fun verify(bytes: ByteArray, expectedText: Boolean, expectedImageCount: Int) {
        XWPFDocument(ByteArrayInputStream(bytes)).use { document ->
            if (expectedText && document.paragraphs.none { it.text.isNotBlank() } && document.tables.isEmpty()) {
                throw ConversionException.Io("Generated DOCX contains no readable text")
            }
            if (document.allPictures.size < expectedImageCount) {
                throw ConversionException.Io("Generated DOCX lost images: expected $expectedImageCount, found ${document.allPictures.size}")
            }
        }
    }

    private fun writePage(document: XWPFDocument, page: EditablePage) {
        val blocks = buildList<PageBlock> {
            page.pageGraphics?.let { add(PageBlock.Image(it)) }
            page.images.forEach { add(PageBlock.Image(it)) }
            page.paragraphs.forEach { paragraph ->
                if (paragraph.tableCells == null) add(PageBlock.Text(paragraph))
            }
        }.sortedBy(PageBlock::top)

        var previousBottom = 0f
        blocks.forEach { block ->
            when (block) {
                is PageBlock.Text -> {
                    val paragraph = document.createParagraph()
                    position(paragraph, block.value.leftPoints, block.value.topPoints, previousBottom)
                    block.value.runs.forEach { source ->
                        paragraph.createRun().apply {
                            setText(source.text)
                            source.fontFamily?.takeIf { it.isNotBlank() }?.let { fontFamily = it }
                            source.fontSizePt?.takeIf { it > 0f }?.let { setFontSize(it.toDouble()) }
                            isBold = source.bold
                            isItalic = source.italic
                        }
                    }
                    previousBottom = maxOf(previousBottom, block.bottom)
                }
                is PageBlock.Image -> {
                    val image = block.value
                    val paragraph = document.createParagraph()
                    paragraph.spacingBefore = 0
                    paragraph.spacingAfter = 0
                    val run = paragraph.createRun()
                    run.addPicture(
                        ByteArrayInputStream(image.png),
                        Document.PICTURE_TYPE_PNG,
                        "pdf-image.png",
                        Units.toEMU(image.widthPoints.toDouble()),
                        Units.toEMU(image.heightPoints.toDouble()),
                    )
                    makeFloating(run, image, behindText = page.pageGraphics === image)
                    previousBottom = maxOf(previousBottom, block.bottom)
                }
            }
        }

        val rows = page.paragraphs.filter { it.tableCells != null }
        if (rows.isNotEmpty()) {
            val columns = rows.maxOf { it.tableCells.orEmpty().size }.coerceAtLeast(1)
            val table = document.createTable(rows.size, columns)
            rows.forEachIndexed { rowIndex, row ->
                row.tableCells.orEmpty().forEachIndexed { columnIndex, cell ->
                    val target = table.getRow(rowIndex).getCell(columnIndex)
                    target.removeParagraph(0)
                    val paragraph = target.addParagraph()
                    cell.runs.forEach { source -> paragraph.createRun().setText(source.text) }
                }
            }
        }
    }

    private fun makeFloating(run: XWPFRun, image: EmbeddedImage, behindText: Boolean) {
        val drawing = run.ctr.getDrawingArray(0)
        if (drawing.sizeOfInlineArray() == 0) return
        val inline = drawing.getInlineArray(0)
        val anchor = drawing.addNewAnchor()
        anchor.setExtent(inline.extent)
        anchor.setDocPr(inline.docPr)
        if (inline.isSetCNvGraphicFramePr) anchor.setCNvGraphicFramePr(inline.cNvGraphicFramePr)
        anchor.setGraphic(inline.graphic)
        anchor.addNewSimplePos().apply { x = 0L; y = 0L }
        anchor.simplePos2 = false
        anchor.addNewPositionH().apply {
            relativeFrom = STRelFromH.PAGE
            posOffset = Units.toEMU(image.leftPoints.toDouble())
        }
        anchor.addNewPositionV().apply {
            relativeFrom = STRelFromV.PAGE
            posOffset = Units.toEMU(image.topPoints.toDouble())
        }
        anchor.addNewWrapNone()
        anchor.distT = 0L
        anchor.distB = 0L
        anchor.distL = 0L
        anchor.distR = 0L
        anchor.relativeHeight = if (behindText) 0L else 1_000L
        anchor.behindDoc = behindText
        anchor.locked = false
        anchor.layoutInCell = false
        anchor.allowOverlap = true
        drawing.removeInline(0)
    }

    private fun position(paragraph: XWPFParagraph, left: Float?, top: Float?, previousBottom: Float) {
        paragraph.indentationLeft = ((left ?: 0f).coerceAtLeast(0f) * 20f).toInt()
        paragraph.spacingBefore = (((top ?: previousBottom) - previousBottom).coerceAtLeast(0f) * 20f).toInt()
        paragraph.spacingAfter = 0
    }

    private fun configureLastSection(document: XWPFDocument, page: EditablePage) {
        val body = document.document.body
        val section = if (body.isSetSectPr) body.sectPr else body.addNewSectPr()
        val size: CTPageSz = if (section.isSetPgSz) section.pgSz else section.addNewPgSz()
        size.w = BigInteger.valueOf((page.widthPoints * 20L))
        size.h = BigInteger.valueOf((page.heightPoints * 20L))
        val margins: CTPageMar = if (section.isSetPgMar) section.pgMar else section.addNewPgMar()
        margins.top = BigInteger.ZERO
        margins.right = BigInteger.ZERO
        margins.bottom = BigInteger.ZERO
        margins.left = BigInteger.ZERO
        margins.header = BigInteger.ZERO
        margins.footer = BigInteger.ZERO
        margins.gutter = BigInteger.ZERO
    }

    private sealed interface PageBlock {
        val top: Float
        val bottom: Float
        data class Text(val value: OcrParagraph) : PageBlock {
            override val top = value.topPoints ?: 0f
            override val bottom = top + (value.heightPoints ?: 12f)
        }
        data class Image(val value: EmbeddedImage) : PageBlock {
            override val top = value.topPoints
            override val bottom = top + value.heightPoints
        }
    }
}
