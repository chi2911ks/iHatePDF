package com.ihatepdf.converter

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class RenderedPage(val image: ByteArray, val widthPoints: Int, val heightPoints: Int)

internal object VisualDocxWriter {
    fun write(pages: List<RenderedPage>, output: OutputStream) {
        require(pages.isNotEmpty()) { "At least one page is required" }
        ZipOutputStream(output.buffered()).use { zip ->
            zip.text("[Content_Types].xml", contentTypes)
            zip.text("_rels/.rels", packageRelationships)
            zip.text("word/_rels/document.xml.rels", documentRelationships(pages.size))
            zip.text("word/document.xml", document(pages))
            pages.forEachIndexed { index, page -> zip.bytes("word/media/page${index + 1}.jpg", page.image) }
        }
    }

    private fun document(pages: List<RenderedPage>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>""")
        pages.forEachIndexed { index, page ->
            val cx = page.widthPoints.toLong() * 12_700L
            val cy = page.heightPoints.toLong() * 12_700L
            val sectionBreak = if (index < pages.lastIndex) sectionProperties(page, nextPage = true) else ""
            append("""<w:p><w:pPr><w:spacing w:before="0" w:after="0"/>$sectionBreak</w:pPr><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="$cx" cy="$cy"/><wp:docPr id="${index + 1}" name="PDF page ${index + 1}"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="${index + 1}" name="page${index + 1}.jpg"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rId${index + 1}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>""")
        }
        append(sectionProperties(pages.last(), nextPage = false))
        append("</w:body></w:document>")
    }

    private fun sectionProperties(page: RenderedPage, nextPage: Boolean): String {
        val type = if (nextPage) """<w:type w:val="nextPage"/>""" else ""
        return """<w:sectPr><w:pgSz w:w="${page.widthPoints * 20}" w:h="${page.heightPoints * 20}"/><w:pgMar w:top="0" w:right="0" w:bottom="0" w:left="0" w:header="0" w:footer="0" w:gutter="0"/>$type</w:sectPr>"""
    }

    private const val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpg" ContentType="image/jpeg"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
    private const val packageRelationships = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

    private fun documentRelationships(pageCount: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        repeat(pageCount) { index -> append("""<Relationship Id="rId${index + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/page${index + 1}.jpg"/>""") }
        append("</Relationships>")
    }

    private fun ZipOutputStream.text(path: String, value: String) = bytes(path, value.toByteArray(Charsets.UTF_8))
    private fun ZipOutputStream.bytes(path: String, value: ByteArray) {
        putNextEntry(ZipEntry(path)); write(value); closeEntry()
    }
}
