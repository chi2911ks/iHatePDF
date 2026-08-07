package com.ihatepdf.converter

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingOrderResolverTest {
    @Test fun ordersTwoColumnsLeftBeforeRightAndKeepsHeader() {
        val header = line("header", 0f, 600f, 10f)
        val left = (1..3).map { line("left$it", 20f, 250f, 50f + it * 20f) }
        val right = (1..3).map { line("right$it", 350f, 580f, 50f + it * 20f) }
        val interleaved = listOf(header, left[0], right[0], left[1], right[1], left[2], right[2])

        val result = ReadingOrderResolver.order(interleaved, 600f)
        assertEquals(listOf("header", "left1", "left2", "left3", "right1", "right2", "right3"), result.map { it.paragraph.text })
    }

    private fun line(text: String, left: Float, right: Float, top: Float): LayoutLine =
        LayoutLine(OcrParagraph(text), left, right, top, top + 10f)
}
