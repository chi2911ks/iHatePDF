package com.ihatepdf.converter

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordToPdfInstrumentedTest {
    @Test fun convertsGeneratedDocxToReadablePdf() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(context.cacheDir, "word-to-pdf-input.docx")
        val output = File(context.cacheDir, "word-to-pdf-output.pdf")
        XWPFDocument().use { document ->
            document.createParagraph().createRun().apply {
                setText("Hello from Android Word conversion")
                fontFamily = "sans-serif"
                fontSize = 16
                isBold = true
            }
            FileOutputStream(input).use(document::write)
        }

        val result = AndroidDocumentConverter(context).wordToPdf(Uri.fromFile(input), Uri.fromFile(output))
        assertTrue(output.readBytes().take(4).toByteArray().contentEquals("%PDF".toByteArray()))
        assertEquals(1, result.pageCount)
        ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> assertEquals(result.pageCount, renderer.pageCount) }
        }
    }
}
