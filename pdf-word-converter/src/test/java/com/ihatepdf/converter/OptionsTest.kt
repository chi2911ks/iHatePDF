package com.ihatepdf.converter

import org.junit.Assert.assertEquals
import org.junit.Test

class OptionsTest {
    @Test fun defaultsPrioritizeEditableOutput() {
        assertEquals(PdfToDocxMode.EDITABLE, PdfToDocxOptions().mode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeRenderDpi() {
        PdfToDocxOptions(renderDpi = 600)
    }
}
