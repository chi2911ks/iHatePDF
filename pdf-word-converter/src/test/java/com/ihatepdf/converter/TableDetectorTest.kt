package com.ihatepdf.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TableDetectorTest {
    @Test fun recognizesStableThreeByThreeGrid() {
        val lines = (0..2).flatMap { row ->
            (0..2).map { column ->
                val left = 20f + column * 120f
                val top = 50f + row * 20f
                LayoutLine(OcrParagraph("r${row}c$column"), left, left + 60f, top, top + 10f)
            }
        }
        val result = TableDetector.transform(lines)
        assertEquals(3, result.size)
        assertNotNull(result.first().paragraph.tableCells)
        assertEquals(listOf("r0c0", "r0c1", "r0c2"), result.first().paragraph.tableCells?.map { it.text })
    }

    @Test fun leavesTwoColumnsAsNormalLayout() {
        val lines = (0..2).flatMap { row ->
            listOf(
                LayoutLine(OcrParagraph("left$row"), 20f, 200f, row * 20f, row * 20f + 10f),
                LayoutLine(OcrParagraph("right$row"), 320f, 580f, row * 20f, row * 20f + 10f),
            )
        }
        assertEquals(6, TableDetector.transform(lines).size)
    }
}
