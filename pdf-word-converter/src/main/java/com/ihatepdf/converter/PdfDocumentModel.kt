package com.ihatepdf.converter

internal enum class PdfPageKind { TEXT, SCAN, MIXED }

internal data class BoundsPt(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

internal data class PageMetadata(
    val cropLeftPt: Float,
    val cropBottomPt: Float,
    val cropWidthPt: Float,
    val cropHeightPt: Float,
    val rotation: Int,
) {
    val displayWidthPt: Float get() = if (normalizedRotation % 180 == 0) cropWidthPt else cropHeightPt
    val displayHeightPt: Float get() = if (normalizedRotation % 180 == 0) cropHeightPt else cropWidthPt
    val normalizedRotation: Int get() = ((rotation % 360) + 360) % 360
}

internal object PdfCoordinateMapper {
    fun fromPdfPoint(x: Float, y: Float, metadata: PageMetadata): Pair<Float, Float> {
        val localX = x - metadata.cropLeftPt
        val localY = y - metadata.cropBottomPt
        return when (metadata.normalizedRotation) {
            90 -> localY to localX
            180 -> (metadata.cropWidthPt - localX) to localY
            270 -> (metadata.cropHeightPt - localY) to (metadata.cropWidthPt - localX)
            else -> localX to (metadata.cropHeightPt - localY)
        }
    }

    fun fromPdfBounds(left: Float, bottom: Float, width: Float, height: Float, metadata: PageMetadata): BoundsPt {
        val corners = listOf(
            fromPdfPoint(left, bottom, metadata),
            fromPdfPoint(left + width, bottom, metadata),
            fromPdfPoint(left, bottom + height, metadata),
            fromPdfPoint(left + width, bottom + height, metadata),
        )
        val minX = corners.minOf { it.first }
        val maxX = corners.maxOf { it.first }
        val minY = corners.minOf { it.second }
        val maxY = corners.maxOf { it.second }
        return BoundsPt(minX, minY, maxX - minX, maxY - minY)
    }
}

internal data class PdfPageModel(
    val metadata: PageMetadata,
    val kind: PdfPageKind,
    val paragraphs: List<OcrParagraph>,
    val images: List<EmbeddedImage>,
)

