package com.ihatepdf.converter

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealPdfDiagnosticInstrumentedTest {
    @Test fun convertsOptionalDiagnosticPdf() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(context.getExternalFilesDir(null), "diagnostic-input.pdf")
        assumeTrue("No optional diagnostic PDF installed", input.isFile)
        val output = File(context.cacheDir, "diagnostic-output.docx")
        val result = AndroidDocumentConverter(context).pdfToDocx(Uri.fromFile(input), Uri.fromFile(output))
        assertTrue(result.pageCount > 0)
        assertTrue(output.length() > 0)
    }
}
