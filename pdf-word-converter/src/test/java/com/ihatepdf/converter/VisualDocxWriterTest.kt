package com.ihatepdf.converter

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualDocxWriterTest {
    @Test fun writesMinimalOpenXmlPackage() {
        val output = ByteArrayOutputStream()
        VisualDocxWriter.write(listOf(RenderedPage(byteArrayOf(1, 2, 3), 612, 792)), output)

        val entries = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries += entry.name
                entry = zip.nextEntry
            }
        }

        assertTrue("[Content_Types].xml" in entries)
        assertTrue("word/document.xml" in entries)
        assertTrue("word/media/page1.jpg" in entries)
    }
}
