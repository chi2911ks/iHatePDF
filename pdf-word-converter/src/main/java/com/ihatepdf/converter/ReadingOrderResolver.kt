package com.ihatepdf.converter

internal data class LayoutLine(
    val paragraph: OcrParagraph,
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
)

internal object ReadingOrderResolver {
    fun order(lines: List<LayoutLine>, pageWidth: Float): List<LayoutLine> {
        if (lines.size < 6 || pageWidth <= 0f) return lines.sortedBy { it.top }
        val leftLines = lines.filter { it.right <= pageWidth * 0.58f }
        val rightLines = lines.filter { it.left >= pageWidth * 0.42f }
        val crossing = lines.filter { it !in leftLines && it !in rightLines }
        if (leftLines.size < 3 || rightLines.size < 3 || crossing.size > lines.size / 3) {
            return lines.sortedWith(compareBy<LayoutLine> { it.top }.thenBy { it.left })
        }

        val columnTop = minOf(leftLines.minOf { it.top }, rightLines.minOf { it.top })
        val columnBottom = maxOf(leftLines.maxOf { it.bottom }, rightLines.maxOf { it.bottom })
        val header = crossing.filter { it.bottom <= columnTop }.sortedBy { it.top }
        val footer = crossing.filter { it.top >= columnBottom }.sortedBy { it.top }
        val middleCrossing = crossing.filter { it !in header && it !in footer }
            .sortedWith(compareBy<LayoutLine> { it.top }.thenBy { it.left })

        return header +
            leftLines.sortedBy { it.top } +
            middleCrossing +
            rightLines.sortedBy { it.top } +
            footer
    }
}
