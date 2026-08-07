package com.ihatepdf.converter

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

internal object WordPdfRenderer {
    fun render(
        model: WordModel,
        output: OutputStream,
        maxPages: Int,
        progress: ConversionProgressListener,
    ): Int {
        val state = RenderState(PdfDocument(), maxPages)
        try {
            if (model.blocks.isEmpty()) state.ensurePage()
            model.blocks.forEachIndexed { index, block ->
                when (block) {
                    is WordBlock.Paragraph -> state.drawParagraph(block)
                    is WordBlock.Image -> state.drawImage(block)
                    is WordBlock.Table -> state.drawTable(block)
                }
                progress.onProgress(ConversionProgress(ConversionStage.WRITING, index + 1, model.blocks.size))
            }
            state.finishCurrentPage()
            state.document.writeTo(output)
            return state.pageCount
        } finally {
            state.document.close()
        }
    }

    private class RenderState(val document: PdfDocument, private val maxPages: Int) {
        var pageCount = 0
            private set
        private var currentPage: PdfDocument.Page? = null
        private val canvas: Canvas get() = currentPage!!.canvas
        private var y = TOP_MARGIN

        fun ensurePage(requiredHeight: Float = DEFAULT_LINE_HEIGHT) {
            if (currentPage != null && y + requiredHeight <= PAGE_HEIGHT - BOTTOM_MARGIN) return
            finishCurrentPage()
            if (pageCount >= maxPages) throw ConversionException.LimitExceeded("Word output exceeds $maxPages pages")
            pageCount++
            currentPage = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount).create())
            y = TOP_MARGIN
        }

        fun finishCurrentPage() {
            currentPage?.let(document::finishPage)
            currentPage = null
        }

        fun drawParagraph(paragraph: WordBlock.Paragraph) {
            ensurePage()
            var x = LEFT_MARGIN
            var lineHeight = DEFAULT_LINE_HEIGHT
            if (paragraph.runs.isEmpty()) {
                y += DEFAULT_LINE_HEIGHT
                return
            }
            paragraph.runs.forEach { run ->
                val paint = paintFor(run)
                lineHeight = max(lineHeight, paint.fontSpacing)
                tokenize(run.text).forEach { token ->
                    if (token == "\n") {
                        x = LEFT_MARGIN
                        y += lineHeight
                        lineHeight = paint.fontSpacing
                        ensurePage(lineHeight)
                    } else {
                        val width = paint.measureText(token)
                        if (!token.isBlank() && x > LEFT_MARGIN && x + width > PAGE_WIDTH - RIGHT_MARGIN) {
                            x = LEFT_MARGIN
                            y += lineHeight
                            lineHeight = paint.fontSpacing
                            ensurePage(lineHeight)
                        }
                        if (!(token.isBlank() && x == LEFT_MARGIN)) {
                            canvas.drawText(token, x, y - paint.fontMetrics.ascent, paint)
                            x += width
                        }
                    }
                }
            }
            y += lineHeight + PARAGRAPH_SPACING
        }

        fun drawImage(imageBlock: WordBlock.Image) {
            val bitmap = BitmapFactory.decodeByteArray(imageBlock.bytes, 0, imageBlock.bytes.size) ?: return
            try {
                val maxWidth = PAGE_WIDTH - LEFT_MARGIN - RIGHT_MARGIN
                val maxHeight = PAGE_HEIGHT - TOP_MARGIN - BOTTOM_MARGIN
                val scale = min(maxWidth / bitmap.width.toFloat(), maxHeight / bitmap.height.toFloat()).coerceAtMost(1f)
                val width = bitmap.width * scale
                val height = bitmap.height * scale
                ensurePage(height + PARAGRAPH_SPACING)
                canvas.drawBitmap(bitmap, null, RectF(LEFT_MARGIN, y, LEFT_MARGIN + width, y + height), IMAGE_PAINT)
                y += height + PARAGRAPH_SPACING
            } finally {
                bitmap.recycle()
            }
        }

        fun drawTable(table: WordBlock.Table) {
            val columnCount = table.rows.maxOfOrNull { it.size } ?: return
            if (columnCount == 0) return
            val tableWidth = PAGE_WIDTH - LEFT_MARGIN - RIGHT_MARGIN
            val columnWidth = tableWidth / columnCount
            val paint = DEFAULT_TEXT_PAINT
            table.rows.forEach { row ->
                val wrapped = (0 until columnCount).map { column ->
                    wrapText(row.getOrNull(column).orEmpty(), paint, columnWidth - CELL_PADDING * 2)
                }
                val rowHeight = max(MIN_ROW_HEIGHT, wrapped.maxOfOrNull { it.size }?.times(paint.fontSpacing)?.plus(CELL_PADDING * 2) ?: MIN_ROW_HEIGHT)
                ensurePage(rowHeight)
                wrapped.forEachIndexed { column, lines ->
                    val left = LEFT_MARGIN + column * columnWidth
                    canvas.drawRect(left, y, left + columnWidth, y + rowHeight, TABLE_PAINT)
                    lines.forEachIndexed { lineIndex, text ->
                        canvas.drawText(text, left + CELL_PADDING, y + CELL_PADDING - paint.fontMetrics.ascent + lineIndex * paint.fontSpacing, paint)
                    }
                }
                y += rowHeight
            }
            y += PARAGRAPH_SPACING
        }

        private fun paintFor(run: WordRun): Paint {
            val style = when {
                run.bold && run.italic -> Typeface.BOLD_ITALIC
                run.bold -> Typeface.BOLD
                run.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            return Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                textSize = run.fontSizePt.coerceIn(4f, 96f)
                typeface = Typeface.create(run.fontFamily ?: "sans-serif", style)
            }
        }
    }

    private fun tokenize(text: String): List<String> = Regex("[^\\s]+|[ \\t]+|\\n").findAll(text.replace("\r\n", "\n").replace('\r', '\n')).map { it.value }.toList()

    private fun wrapText(text: String, paint: Paint, width: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var current = ""
        tokenize(text).forEach { token ->
            if (token == "\n" || (current.isNotEmpty() && paint.measureText(current + token) > width)) {
                lines += current.trimEnd()
                current = if (token == "\n") "" else token.trimStart()
            } else current += token
        }
        if (current.isNotEmpty() || lines.isEmpty()) lines += current.trimEnd()
        return lines
    }

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val LEFT_MARGIN = 48f
    private const val RIGHT_MARGIN = 48f
    private const val TOP_MARGIN = 48f
    private const val BOTTOM_MARGIN = 48f
    private const val DEFAULT_LINE_HEIGHT = 14f
    private const val PARAGRAPH_SPACING = 6f
    private const val CELL_PADDING = 4f
    private const val MIN_ROW_HEIGHT = 24f
    private val DEFAULT_TEXT_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 10f }
    private val IMAGE_PAINT = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val TABLE_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.75f; color = android.graphics.Color.DKGRAY }
}
