package com.ihatepdf.converter

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object EditableDocxWriter {
    fun write(pages: List<EditablePage>, output: OutputStream) {
        require(pages.isNotEmpty()) { "At least one page is required" }
        val images = pages.flatMap { page -> listOfNotNull(page.pageGraphics) + page.images }
        ZipOutputStream(output.buffered()).use { zip ->
            zip.text("[Content_Types].xml", CONTENT_TYPES)
            zip.text("_rels/.rels", PACKAGE_RELATIONSHIPS)
            if (images.isNotEmpty()) zip.text("word/_rels/document.xml.rels", documentRelationships(images.size))
            zip.text("word/document.xml", document(pages))
            images.forEachIndexed { index, image -> zip.bytes("word/media/image${index + 1}.png", image.png) }
        }
    }

    private fun document(pages: List<EditablePage>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:w10="urn:schemas-microsoft-com:office:word"><w:body>""")
        var imageId = 1
        pages.forEachIndexed { pageIndex, page ->
            var previousBottomPoints = 0f
            page.pageGraphics?.let { background ->
                append(anchoredImageXml(background, imageId, behindText = true, name = "PDF page graphics"))
                imageId++
            }
            page.images.forEach { image ->
                append(anchoredImageXml(image, imageId, behindText = true, name = "PDF image"))
                imageId++
            }
            if (page.paragraphs.isEmpty()) append("<w:p/>")
            var paragraphIndex = 0
            while (paragraphIndex < page.paragraphs.size) {
                val paragraph = page.paragraphs[paragraphIndex]
                if (paragraph.tableCells != null) {
                    val rows = mutableListOf<OcrParagraph>()
                    while (paragraphIndex < page.paragraphs.size && page.paragraphs[paragraphIndex].tableCells != null) {
                        rows += page.paragraphs[paragraphIndex++]
                    }
                    append(tableXml(rows, page))
                } else {
                    append("<w:p>")
                    append(paragraphProperties(paragraph, previousBottomPoints, page))
                    paragraph.runs.forEach { run -> append(run.toWordXml()) }
                    append("</w:p>")
                    paragraphIndex++
                }
                val top = paragraph.topPoints
                val height = paragraph.heightPoints
                if (top != null && height != null) previousBottomPoints = top + height
            }
            if (pageIndex < pages.lastIndex) {
                append("""<w:p><w:pPr>${sectionProperties(page, true)}</w:pPr></w:p>""")
            }
        }
        append(sectionProperties(pages.last(), false))
        append("</w:body></w:document>")
    }

    private fun sectionProperties(page: EditablePage, nextPage: Boolean): String {
        val type = if (nextPage) """<w:type w:val="nextPage"/>""" else ""
        return """<w:sectPr><w:pgSz w:w="${page.widthPoints * 20}" w:h="${page.heightPoints * 20}"/><w:pgMar w:top="0" w:right="0" w:bottom="0" w:left="0" w:header="0" w:footer="0" w:gutter="0"/>$type</w:sectPr>"""
    }

    private fun paragraphProperties(paragraph: OcrParagraph, previousBottom: Float, page: EditablePage): String {
        val properties = buildString {
            paragraph.leftPoints?.coerceIn(0f, page.widthPoints.toFloat())?.let { left ->
                append("""<w:ind w:left="${(left * 20f).toInt()}"/>""")
            }
            paragraph.topPoints?.let { top ->
                val before = ((top - previousBottom).coerceIn(0f, page.heightPoints.toFloat()) * 20f).toInt()
                val line = ((paragraph.heightPoints ?: 0f).coerceAtLeast(0f) * 20f).toInt()
                append("""<w:spacing w:before="$before" w:after="0"""")
                if (line > 0) append(""" w:line="$line" w:lineRule="atLeast"""")
                append("/>")
            }
        }
        return if (properties.isEmpty()) "" else "<w:pPr>$properties</w:pPr>"
    }

    private fun StyledTextRun.toWordXml(): String = buildString {
        append("<w:r>")
        if (fontFamily != null || fontSizePt != null || bold || italic) {
            append("<w:rPr>")
            fontFamily?.takeIf(String::isNotBlank)?.let { font ->
                val safeFont = font.escapeXml()
                append("""<w:rFonts w:ascii="$safeFont" w:hAnsi="$safeFont"/>""")
            }
            fontSizePt?.takeIf { it > 0f }?.let { size ->
                val halfPoints = (size * 2f).toInt().coerceIn(2, 3276)
                append("""<w:sz w:val="$halfPoints"/><w:szCs w:val="$halfPoints"/>""")
            }
            if (bold) append("<w:b/>")
            if (italic) append("<w:i/>")
            append("</w:rPr>")
        }
        append("""<w:t xml:space="preserve">""")
        append(text.escapeXml())
        append("</w:t></w:r>")
    }

    private fun tableXml(rows: List<OcrParagraph>, page: EditablePage): String = buildString {
        val columnCount = rows.firstOrNull()?.tableCells?.size ?: return@buildString
        val left = rows.first().leftPoints?.coerceAtLeast(0f) ?: 0f
        val availableWidthTwips = ((page.widthPoints - left).coerceAtLeast(1f) * 20f).toInt()
        val columnWidth = (availableWidthTwips / columnCount).coerceAtLeast(1)
        append("<w:tbl><w:tblPr>")
        append("""<w:tblW w:w="$availableWidthTwips" w:type="dxa"/><w:tblInd w:w="${(left * 20f).toInt()}" w:type="dxa"/>""")
        append("""<w:tblBorders><w:top w:val="nil"/><w:left w:val="nil"/><w:bottom w:val="nil"/><w:right w:val="nil"/><w:insideH w:val="nil"/><w:insideV w:val="nil"/></w:tblBorders>""")
        append("</w:tblPr><w:tblGrid>")
        repeat(columnCount) { append("""<w:gridCol w:w="$columnWidth"/>""") }
        append("</w:tblGrid>")
        rows.forEach { row ->
            append("<w:tr>")
            row.tableCells.orEmpty().forEach { cell ->
                append("""<w:tc><w:tcPr><w:tcW w:w="$columnWidth" w:type="dxa"/></w:tcPr><w:p>""")
                cell.runs.forEach { run -> append(run.toWordXml()) }
                append("</w:p></w:tc>")
            }
            append("</w:tr>")
        }
        append("</w:tbl>")
    }

    private fun anchoredImageXml(image: EmbeddedImage, id: Int, behindText: Boolean, name: String): String {
        val x = (image.leftPoints * 12_700f).toLong()
        val y = (image.topPoints * 12_700f).toLong()
        val width = (image.widthPoints * 12_700f).toLong().coerceAtLeast(1L)
        val height = (image.heightPoints * 12_700f).toLong().coerceAtLeast(1L)
        val behind = if (behindText) 1 else 0
        return """<w:p><w:pPr><w:spacing w:before="0" w:after="0" w:line="1" w:lineRule="exact"/></w:pPr><w:r><w:drawing><wp:anchor distT="0" distB="0" distL="0" distR="0" simplePos="0" relativeHeight="$id" behindDoc="$behind" locked="1" layoutInCell="0" allowOverlap="1"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="page"><wp:posOffset>$x</wp:posOffset></wp:positionH><wp:positionV relativeFrom="page"><wp:posOffset>$y</wp:posOffset></wp:positionV><wp:extent cx="$width" cy="$height"/><wp:effectExtent l="0" t="0" r="0" b="0"/><wp:wrapNone/><wp:docPr id="$id" name="$name $id"/><wp:cNvGraphicFramePr/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="$id" name="image$id.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rId$id"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$width" cy="$height"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r></w:p>"""
    }

    private fun String.escapeXml(): String = buildString(length) {
        this@escapeXml.forEach { char ->
            append(when (char) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&apos;"
                else -> char
            })
        }
    }

    private fun ZipOutputStream.text(path: String, value: String) {
        bytes(path, value.toByteArray(Charsets.UTF_8))
    }

    private fun ZipOutputStream.bytes(path: String, value: ByteArray) {
        putNextEntry(ZipEntry(path)); write(value); closeEntry()
    }

    private fun documentRelationships(imageCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        repeat(imageCount) { index ->
            append("""<Relationship Id="rId${index + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image${index + 1}.png"/>""")
        }
        append("</Relationships>")
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
    private const val PACKAGE_RELATIONSHIPS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
}
