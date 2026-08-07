package com.ihatepdf.converter

import android.net.Uri

interface DocumentConverter {
    suspend fun pdfToDocx(input: Uri, output: Uri, options: PdfToDocxOptions = PdfToDocxOptions(), progress: ConversionProgressListener = ConversionProgressListener.NONE): ConversionResult
    suspend fun wordToPdf(input: Uri, output: Uri, options: WordToPdfOptions = WordToPdfOptions(), progress: ConversionProgressListener = ConversionProgressListener.NONE): ConversionResult
}

enum class PdfToDocxMode { EDITABLE, VISUAL }

data class PdfToDocxOptions(
    val mode: PdfToDocxMode = PdfToDocxMode.EDITABLE,
    val renderDpi: Int = 200,
    val jpegQuality: Int = 92,
    val maxPages: Int = 300,
    val enableOcr: Boolean = true,
    val preservePageGraphics: Boolean = true,
) {
    init {
        require(renderDpi in 72..300) { "renderDpi must be between 72 and 300" }
        require(jpegQuality in 1..100) { "jpegQuality must be between 1 and 100" }
        require(maxPages in 1..300) { "maxPages must be between 1 and 300" }
    }
}

data class WordToPdfOptions(val maxPages: Int = 300) {
    init { require(maxPages in 1..300) { "maxPages must be between 1 and 300" } }
}

fun interface ConversionProgressListener {
    fun onProgress(progress: ConversionProgress)
    companion object { val NONE = ConversionProgressListener { } }
}

data class ConversionProgress(val stage: ConversionStage, val completedUnits: Int, val totalUnits: Int) {
    val fraction: Float get() = if (totalUnits == 0) 0f else completedUnits.toFloat() / totalUnits
}

enum class ConversionStage { VALIDATING, RENDERING, OCR, WRITING, VERIFYING }

data class ConversionResult(val output: Uri, val pageCount: Int, val warnings: List<ConversionWarning> = emptyList())
data class ConversionWarning(val code: String, val message: String, val pageIndex: Int? = null)
