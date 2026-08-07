package com.ihatepdf.converter

import java.io.ByteArrayOutputStream
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordParserTest {
    @Test fun parsesDocxRunsAndTable() {
        val bytes = ByteArrayOutputStream().use { output ->
            XWPFDocument().use { document ->
                document.createParagraph().createRun().apply {
                    setText("Hello")
                    fontFamily = "Arial"
                    fontSize = 14
                    isBold = true
                }
                document.createTable(2, 2).apply {
                    getRow(0).getCell(0).text = "A"
                    getRow(0).getCell(1).text = "B"
                    getRow(1).getCell(0).text = "C"
                    getRow(1).getCell(1).text = "D"
                }
                document.write(output)
            }
            output.toByteArray()
        }

        val model = WordParser.parse(bytes)
        val paragraph = model.blocks.filterIsInstance<WordBlock.Paragraph>().first()
        assertEquals("Hello", paragraph.runs.first().text)
        assertEquals("Arial", paragraph.runs.first().fontFamily)
        assertEquals(14f, paragraph.runs.first().fontSizePt)
        assertTrue(paragraph.runs.first().bold)
        val table = model.blocks.filterIsInstance<WordBlock.Table>().first()
        assertEquals(listOf("A", "B"), table.rows.first())
    }

    @Test(expected = ConversionException.InvalidInput::class)
    fun rejectsUnknownInput() {
        WordParser.parse("not word".toByteArray())
    }
}
