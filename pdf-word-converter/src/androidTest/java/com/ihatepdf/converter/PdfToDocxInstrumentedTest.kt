package com.ihatepdf.converter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfToDocxInstrumentedTest {
    @Test fun defaultEditableKeepsContentOnPageWithoutFullPageImage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(context.cacheDir, "positioned-input.pdf")
        val output = File(context.cacheDir, "positioned-output.docx")
        createPdf(input)

        AndroidDocumentConverter(context).pdfToDocx(
            Uri.fromFile(input), Uri.fromFile(output), PdfToDocxOptions(mode = PdfToDocxMode.EDITABLE)
        )

        XWPFDocument(FileInputStream(output)).use { document ->
            assertTrue(document.paragraphs.joinToString("\n") { it.text }.contains("Editable PDF text"))
            assertTrue(document.allPictures.isNotEmpty())
            val graphics = BitmapFactory.decodeByteArray(
                document.allPictures.first().data, 0, document.allPictures.first().data.size
            )
            try {
                assertEquals(0, Color.alpha(graphics.getPixel(20, 20)))
                assertTrue(hasOpaquePixelNear(graphics, 96, 240, 20))
                assertTrue(hasOpaquePixelNear(graphics, 440, 240, 20))
            } finally {
                graphics.recycle()
            }
        }
    }

    @Test fun editableConversionPreservesTextAndRasterImage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(context.cacheDir, "pdf-with-image.pdf")
        val output = File(context.cacheDir, "editable-with-image.docx")
        createPdf(input)

        val result = AndroidDocumentConverter(context).pdfToDocx(
            Uri.fromFile(input),
            Uri.fromFile(output),
            PdfToDocxOptions(mode = PdfToDocxMode.EDITABLE, preservePageGraphics = true),
        )

        assertEquals(1, result.pageCount)
        assertTrue(output.length() > 0)
        XWPFDocument(FileInputStream(output)).use { document ->
            assertTrue(document.paragraphs.joinToString("\n") { it.text }.contains("Editable PDF text"))
            assertTrue(document.allPictures.isNotEmpty())
        }
    }

    private fun createPdf(file: File) {
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            page.canvas.drawText("Editable PDF text", 48f, 80f, Paint().apply {
                color = Color.BLACK
                textSize = 20f
            })
            val bitmap = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(20, 120, 220))
            }
            page.canvas.drawBitmap(bitmap, 48f, 120f, null)
            page.canvas.drawRect(220f, 120f, 480f, 180f, Paint().apply {
                color = Color.RED
                style = Paint.Style.STROKE
                strokeWidth = 8f
            })
            bitmap.recycle()
            document.finishPage(page)
            FileOutputStream(file).use(document::writeTo)
        } finally {
            document.close()
        }
    }

    private fun hasOpaquePixelNear(bitmap: Bitmap, centerX: Int, centerY: Int, radius: Int): Boolean {
        val left = (centerX - radius).coerceAtLeast(0)
        val right = (centerX + radius).coerceAtMost(bitmap.width - 1)
        val top = (centerY - radius).coerceAtLeast(0)
        val bottom = (centerY + radius).coerceAtMost(bitmap.height - 1)
        for (y in top..bottom) for (x in left..right) {
            if (Color.alpha(bitmap.getPixel(x, y)) > 0) return true
        }
        return false
    }
}
