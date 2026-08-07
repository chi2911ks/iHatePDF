package com.ihatepdf.converter

import kotlin.math.abs

internal object TableDetector {
    fun transform(lines: List<LayoutLine>): List<LayoutLine> {
        val rowCandidates = groupRows(lines).filter { it.size >= MIN_COLUMNS }
        if (rowCandidates.size < MIN_ROWS) return lines

        val clusters = mutableListOf<MutableList<List<LayoutLine>>>()
        rowCandidates.sortedBy { it.minOf(LayoutLine::top) }.forEach { row ->
            val cluster = clusters.firstOrNull { matchesColumns(it.first(), row) }
            if (cluster == null) clusters += mutableListOf(row) else cluster += row
        }
        val tableRows = clusters.maxByOrNull { it.size }?.takeIf { it.size >= MIN_ROWS } ?: return lines
        val used = tableRows.flatten().toSet()
        val replacements = tableRows.map { row ->
            val cells = row.sortedBy { it.left }.map { it.paragraph }
            val left = row.minOf { it.left }
            val right = row.maxOf { it.right }
            val top = row.minOf { it.top }
            val bottom = row.maxOf { it.bottom }
            LayoutLine(
                paragraph = OcrParagraph(
                    runs = emptyList(),
                    leftPoints = left,
                    topPoints = top,
                    heightPoints = bottom - top,
                    tableCells = cells,
                ),
                left = left,
                right = right,
                top = top,
                bottom = bottom,
            )
        }
        return (lines.filterNot { it in used } + replacements)
            .sortedWith(compareBy<LayoutLine> { it.top }.thenBy { it.left })
    }

    private fun groupRows(lines: List<LayoutLine>): List<List<LayoutLine>> {
        val rows = mutableListOf<MutableList<LayoutLine>>()
        lines.sortedWith(compareBy<LayoutLine> { it.top }.thenBy { it.left }).forEach { line ->
            val row = rows.lastOrNull()
            if (row == null || abs(row.first().top - line.top) > ROW_TOLERANCE_POINTS) {
                rows += mutableListOf(line)
            } else row += line
        }
        return rows
    }

    private fun matchesColumns(first: List<LayoutLine>, other: List<LayoutLine>): Boolean {
        if (first.size != other.size) return false
        val firstSorted = first.sortedBy { it.left }
        val otherSorted = other.sortedBy { it.left }
        return firstSorted.indices.all { index ->
            abs(firstSorted[index].left - otherSorted[index].left) <= COLUMN_TOLERANCE_POINTS
        }
    }

    private const val MIN_COLUMNS = 3
    private const val MIN_ROWS = 3
    private const val ROW_TOLERANCE_POINTS = 4f
    private const val COLUMN_TOLERANCE_POINTS = 18f
}
