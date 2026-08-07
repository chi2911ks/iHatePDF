package com.ihatepdf.converter

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.util.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoiDocxWriterTest {
    @Test fun createsReadableDocxWithTextAndImage() {
        val image = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2nCEAAAAASUVORK5CYII="
        )
        val output = ByteArrayOutputStream()
        PoiDocxWriter.write(
            listOf(EditablePage(listOf(OcrParagraph("Hello POI")), 612, 792, listOf(EmbeddedImage(image, 20f, 30f, 10f, 10f)))),
            output,
        )
        XWPFDocument(ByteArrayInputStream(output.toByteArray())).use { document ->
            assertTrue(document.paragraphs.any { it.text.contains("Hello POI") })
            assertEquals(1, document.allPictures.size)
            val anchor = document.paragraphs.flatMap { it.runs }
                .flatMap { it.ctr.drawingList }
                .flatMap { it.anchorList }
                .single()
            assertEquals(Units.toEMU(20.0), anchor.positionH.posOffset)
            assertEquals(Units.toEMU(30.0), anchor.positionV.posOffset)
        }
    }
}
