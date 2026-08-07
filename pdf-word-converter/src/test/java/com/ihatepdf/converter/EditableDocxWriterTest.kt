package com.ihatepdf.converter

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class EditableDocxWriterTest {
    @Test fun writesEditableTextAndEscapesXml() {
        val xml = writeAndRead(OcrParagraph("A & B < C"))
        assertTrue(xml.contains("A &amp; B &lt; C"))
    }

    @Test fun writesFontSizeAndEmphasis() {
        val run = StyledTextRun("Styled", "Arial", 12f, bold = true, italic = true)
        val xml = writeAndRead(OcrParagraph(listOf(run)))
        assertTrue(xml.contains("w:ascii=") && xml.contains("Arial"))
        assertTrue(xml.contains("w:val=") && xml.contains("24"))
        assertTrue(xml.contains("<w:b/>") && xml.contains("<w:i/>"))
    }

    @Test fun writesParagraphPosition() {
        val paragraph = OcrParagraph(
            runs = listOf(StyledTextRun("Positioned")),
            leftPoints = 36f,
            topPoints = 72f,
            heightPoints = 12f,
        )
        val xml = writeAndRead(paragraph)
        assertTrue(xml.contains("w:left=") && xml.contains("720"))
        assertTrue(xml.contains("w:before=") && xml.contains("1440"))
    }

    @Test fun writesDetectedRowsAsWordTable() {
        val cells = listOf(OcrParagraph("A"), OcrParagraph("B"), OcrParagraph("C"))
        val row = OcrParagraph(emptyList(), leftPoints = 20f, tableCells = cells)
        val output = ByteArrayOutputStream()
        EditableDocxWriter.write(listOf(EditablePage(listOf(row, row, row), 612, 792)), output)
        val xml = readDocumentXml(output)
        assertTrue(xml.contains("<w:tbl>"))
        assertTrue(xml.contains("<w:tr>") && xml.contains("<w:tc>"))
    }

    @Test fun packagesAndAnchorsPageImage() {
        val image = EmbeddedImage(byteArrayOf(1, 2, 3), 10f, 20f, 100f, 50f)
        val output = ByteArrayOutputStream()
        EditableDocxWriter.write(listOf(EditablePage(listOf(OcrParagraph("Text")), 612, 792, listOf(image))), output)
        val entries = mutableSetOf<String>()
        var xml = ""
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries += entry.name
                if (entry.name == "word/document.xml") xml = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        assertTrue("word/media/image1.png" in entries)
        assertTrue("word/_rels/document.xml.rels" in entries)
        assertTrue(xml.contains("<wp:anchor") && xml.contains("r:embed="))
    }

    private fun writeAndRead(paragraph: OcrParagraph): String {
        val output = ByteArrayOutputStream()
        EditableDocxWriter.write(listOf(EditablePage(listOf(paragraph), 612, 792)), output)
        return readDocumentXml(output)
    }

    private fun readDocumentXml(output: ByteArrayOutputStream): String {
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") return zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        error("word/document.xml is missing")
    }
}
