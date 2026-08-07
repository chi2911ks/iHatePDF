package com.ihatepdf.converter

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfCoordinateMapperTest {
    @Test fun mapsUnrotatedPdfCoordinatesToTopLeft() {
        val page = PageMetadata(10f, 20f, 200f, 300f, 0)
        val bounds = PdfCoordinateMapper.fromPdfBounds(30f, 260f, 40f, 20f, page)
        assertEquals(20f, bounds.left, 0.01f)
        assertEquals(40f, bounds.top, 0.01f)
        assertEquals(40f, bounds.width, 0.01f)
        assertEquals(20f, bounds.height, 0.01f)
    }

    @Test fun swapsDisplayDimensionsAtNinetyDegrees() {
        val page = PageMetadata(0f, 0f, 200f, 300f, 90)
        assertEquals(300f, page.displayWidthPt, 0.01f)
        assertEquals(200f, page.displayHeightPt, 0.01f)
        val point = PdfCoordinateMapper.fromPdfPoint(25f, 40f, page)
        assertEquals(40f, point.first, 0.01f)
        assertEquals(25f, point.second, 0.01f)
    }
}

