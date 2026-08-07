package com.ihatepdf.converter

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class MlKitOcrEngine : AutoCloseable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap, pageWidthPoints: Int, pageHeightPoints: Int): List<OcrParagraph> {
        val result = suspendCancellableCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }
        val scaleX = pageWidthPoints.toFloat() / bitmap.width
        val scaleY = pageHeightPoints.toFloat() / bitmap.height
        return result.textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
            val text = line.text.trim()
            val box = line.boundingBox
            if (text.isEmpty()) null else OcrParagraph(
                runs = listOf(StyledTextRun(text)),
                leftPoints = box?.left?.times(scaleX),
                topPoints = box?.top?.times(scaleY),
                heightPoints = box?.height()?.times(scaleY),
            )
        }
    }

    override fun close() = recognizer.close()
}

internal data class StyledTextRun(
    val text: String,
    val fontFamily: String? = null,
    val fontSizePt: Float? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
)

internal data class OcrParagraph(
    val runs: List<StyledTextRun>,
    val leftPoints: Float? = null,
    val topPoints: Float? = null,
    val heightPoints: Float? = null,
    val tableCells: List<OcrParagraph>? = null,
) {
    constructor(text: String) : this(listOf(StyledTextRun(text)))
    val text: String get() = tableCells?.joinToString("\t") { it.text }
        ?: runs.joinToString(separator = "") { it.text }
}
internal data class EmbeddedImage(
    val png: ByteArray,
    val leftPoints: Float,
    val topPoints: Float,
    val widthPoints: Float,
    val heightPoints: Float,
)

internal data class EditablePage(
    val paragraphs: List<OcrParagraph>,
    val widthPoints: Int,
    val heightPoints: Int,
    val images: List<EmbeddedImage> = emptyList(),
    val pageGraphics: EmbeddedImage? = null,
)
