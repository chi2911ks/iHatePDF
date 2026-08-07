package com.ihatepdf.converter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Renders only PDF graphics operators (images/path/fill/stroke), never text, to a transparent page layer. */
internal class PdfImageExtractor(
    private val page: PDPage,
    private val metadata: PageMetadata,
    private val scale: Float = 2f,
) : PDFGraphicsStreamEngine(page) {
    private val bitmap = Bitmap.createBitmap(
        max(1, (metadata.displayWidthPt * scale).toInt()),
        max(1, (metadata.displayHeightPt * scale).toInt()),
        Bitmap.Config.ARGB_8888,
    )
    private val canvas = Canvas(bitmap).apply { scale(scale, scale) }
    private var path = Path()
    private var currentPoint = PointF()
    private var hasContent = false

    fun extractGraphicsLayer(): EmbeddedImage? = try {
        processPage(page)
        if (!hasContent) null else {
            val png = ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
            EmbeddedImage(png, 0f, 0f, metadata.displayWidthPt, metadata.displayHeightPt)
        }
    } finally {
        bitmap.recycle()
    }

    override fun drawImage(pdImage: PDImage) {
        val source = pdImage.image ?: return
        try {
            val ctm = graphicsState.currentTransformationMatrix
            val sourcePoints = floatArrayOf(
                0f, 0f,
                source.width.toFloat(), 0f,
                0f, source.height.toFloat(),
                source.width.toFloat(), source.height.toFloat(),
            )
            val targetPoints = floatArrayOf(
                *mappedMatrixPoint(ctm, 0f, 1f),
                *mappedMatrixPoint(ctm, 1f, 1f),
                *mappedMatrixPoint(ctm, 0f, 0f),
                *mappedMatrixPoint(ctm, 1f, 0f),
            )
            val matrix = Matrix()
            if (matrix.setPolyToPoly(sourcePoints, 0, targetPoints, 0, 4)) {
                canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                hasContent = true
            }
        } finally {
            source.recycle()
        }
    }

    override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) {
        val points = listOf(p0, p1, p2, p3).map { mapPoint(it.x, it.y) }
        path.moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { path.lineTo(it.x, it.y) }
        path.close()
        currentPoint = points.last()
    }

    override fun moveTo(x: Float, y: Float) {
        currentPoint = mapPoint(x, y)
        path.moveTo(currentPoint.x, currentPoint.y)
    }

    override fun lineTo(x: Float, y: Float) {
        currentPoint = mapPoint(x, y)
        path.lineTo(currentPoint.x, currentPoint.y)
    }

    override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        val p1 = mapPoint(x1, y1)
        val p2 = mapPoint(x2, y2)
        val p3 = mapPoint(x3, y3)
        path.cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
        currentPoint = p3
    }

    override fun getCurrentPoint(): PointF = currentPoint
    override fun closePath() = path.close()
    override fun endPath() { path.reset() }

    override fun strokePath() {
        drawPath(Paint.Style.STROKE, graphicsState.strokingColor.toRgbSafely())
    }

    override fun fillPath(windingRule: Path.FillType) {
        path.fillType = windingRule
        drawPath(Paint.Style.FILL, graphicsState.nonStrokingColor.toRgbSafely())
    }

    override fun fillAndStrokePath(windingRule: Path.FillType) {
        path.fillType = windingRule
        canvas.drawPath(path, createPaint(Paint.Style.FILL, graphicsState.nonStrokingColor.toRgbSafely()))
        canvas.drawPath(path, createPaint(Paint.Style.STROKE, graphicsState.strokingColor.toRgbSafely()))
        hasContent = true
        path.reset()
    }

    override fun clip(windingRule: Path.FillType) {
        path.fillType = windingRule
        canvas.clipPath(path)
    }

    override fun shadingFill(shadingName: COSName) = Unit

    private fun drawPath(style: Paint.Style, color: Int) {
        canvas.drawPath(path, createPaint(style, color))
        hasContent = true
        path.reset()
    }

    private fun createPaint(style: Paint.Style, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = style
        this.color = color or (0xFF shl 24)
        strokeWidth = graphicsState.lineWidth.coerceAtLeast(0.25f)
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.MITER
    }

    private fun mapPoint(x: Float, y: Float): PointF {
        val mapped = PdfCoordinateMapper.fromPdfPoint(x, y, metadata)
        return PointF(mapped.first, mapped.second)
    }

    private fun mappedMatrixPoint(matrix: com.tom_roush.pdfbox.util.Matrix, x: Float, y: Float): FloatArray {
        val rawX = matrix.scaleX * x + matrix.shearX * y + matrix.translateX
        val rawY = matrix.shearY * x + matrix.scaleY * y + matrix.translateY
        val mapped = PdfCoordinateMapper.fromPdfPoint(rawX, rawY, metadata)
        return floatArrayOf(mapped.first, mapped.second)
    }

    private fun com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor.toRgbSafely(): Int =
        try { colorSpace.toRGB(components).let { Color.rgb((it[0] * 255).toInt(), (it[1] * 255).toInt(), (it[2] * 255).toInt()) } }
        catch (_: Exception) { Color.BLACK }
}
