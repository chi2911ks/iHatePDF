package com.ihatepdf.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

class AndroidDocumentConverter(context: Context) : DocumentConverter {
    private val appContext = context.applicationContext

    override suspend fun pdfToDocx(
        input: Uri,
        output: Uri,
        options: PdfToDocxOptions,
        progress: ConversionProgressListener,
    ): ConversionResult {
        progress.onProgress(ConversionProgress(ConversionStage.VALIDATING, 0, 1))
        val descriptor = appContext.contentResolver.openFileDescriptor(input, "r")
            ?: throw ConversionException.InvalidInput("Cannot open input Uri")
        val editable = options.mode == PdfToDocxMode.EDITABLE
        val ocrEngine = if (editable && options.enableOcr) MlKitOcrEngine() else null
        try {
            val renderedPages = mutableListOf<RenderedPage>()
            val editablePages = mutableListOf<EditablePage>()
            val warnings = mutableListOf<ConversionWarning>()
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) throw ConversionException.InvalidInput("PDF has no pages")
                if (renderer.pageCount > options.maxPages) {
                    throw ConversionException.LimitExceeded(
                        "PDF has ${renderer.pageCount} pages; maximum is ${options.maxPages}"
                    )
                }
                val extractedPages = if (editable) {
                    try {
                        PdfTextExtractor(appContext).extract(input, renderer.pageCount)
                    } catch (error: Exception) {
                        warnings += ConversionWarning(
                            "TEXT_EXTRACTION_FAILED",
                            "Direct PDF text extraction failed; all pages use OCR: ${error.message}"
                        )
                        List(renderer.pageCount) { ExtractedPageContent(emptyList(), emptyList()) }
                    }
                } else emptyList()

                repeat(renderer.pageCount) { index ->
                    coroutineContext.ensureActive()
                    renderer.openPage(index).use { page ->
                        val extractedPage = extractedPages.getOrNull(index)
                        val directParagraphs = extractedPage?.paragraphs.orEmpty()
                        if (editable && extractedPage?.kind != PdfPageKind.SCAN && directParagraphs.sumOf { it.text.length } >= MIN_DIRECT_TEXT_CHARS) {
                            val graphics = if (options.preservePageGraphics) {
                                extractedPage?.graphicsLayer
                            } else null
                            editablePages += EditablePage(
                                directParagraphs,
                                page.width,
                                page.height,
                                images = if (graphics == null) extractedPage?.images.orEmpty() else emptyList(),
                                pageGraphics = graphics,
                            )
                            progress.onProgress(ConversionProgress(ConversionStage.OCR, index + 1, renderer.pageCount))
                            return@use
                        }
                        val scale = options.renderDpi / 72f
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val pixelCount = width.toLong() * height.toLong()
                        if (pixelCount > MAX_PAGE_PIXELS) {
                            throw ConversionException.LimitExceeded(
                                "Page ${index + 1} needs $pixelCount pixels; maximum is $MAX_PAGE_PIXELS"
                            )
                        }
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            if (editable && ocrEngine != null) {
                                progress.onProgress(ConversionProgress(ConversionStage.OCR, index, renderer.pageCount))
                                editablePages += EditablePage(
                                    paragraphs = ocrEngine.recognize(bitmap, page.width, page.height),
                                    widthPoints = page.width,
                                    heightPoints = page.height,
                                    images = if (options.preservePageGraphics) emptyList() else extractedPage?.images.orEmpty(),
                                    pageGraphics = if (options.preservePageGraphics) {
                                        extractedPage?.graphicsLayer ?: bitmap.toPageGraphics(page.width, page.height)
                                    } else null,
                                )
                                progress.onProgress(ConversionProgress(ConversionStage.OCR, index + 1, renderer.pageCount))
                            } else if (!editable) {
                                val image = ByteArrayOutputStream().use { bytes ->
                                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, options.jpegQuality, bytes)) {
                                        throw ConversionException.Io("Cannot encode page ${index + 1}")
                                    }
                                    bytes.toByteArray()
                                }
                                renderedPages += RenderedPage(image, page.width, page.height)
                            } else {
                                throw ConversionException.InvalidInput(
                                    "Page ${index + 1} is scanned and OCR is disabled"
                                )
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    progress.onProgress(ConversionProgress(ConversionStage.RENDERING, index + 1, renderer.pageCount))
                }
            }

            coroutineContext.ensureActive()
            val pageCount = if (editable) editablePages.size else renderedPages.size
            progress.onProgress(ConversionProgress(ConversionStage.WRITING, 0, pageCount))
            if (!editable) {
                appContext.contentResolver.openOutputStream(output, "rwt")?.use { stream ->
                    VisualDocxWriter.write(renderedPages, stream)
                } ?: throw ConversionException.Io("Cannot open output Uri")
            } else {
                val docx = ByteArrayOutputStream().use { buffer ->
                    PoiDocxWriter.write(editablePages, buffer)
                    buffer.toByteArray()
                }
                progress.onProgress(ConversionProgress(ConversionStage.VERIFYING, 0, 1))
                PoiDocxWriter.verify(
                    bytes = docx,
                    expectedText = editablePages.any { page -> page.paragraphs.any { it.text.isNotBlank() } },
                    expectedImageCount = editablePages.sumOf { page -> page.images.size + if (page.pageGraphics != null) 1 else 0 },
                )
                appContext.contentResolver.openOutputStream(output, "rwt")?.use { it.write(docx) }
                    ?: throw ConversionException.Io("Cannot open output Uri")
                progress.onProgress(ConversionProgress(ConversionStage.VERIFYING, 1, 1))
            }
            progress.onProgress(ConversionProgress(ConversionStage.WRITING, pageCount, pageCount))

            return ConversionResult(
                output = output,
                pageCount = pageCount,
                warnings = if (!editable) {
                    warnings + ConversionWarning("VISUAL_NOT_EDITABLE", "Each PDF page is embedded as an image.")
                } else {
                    warnings + listOf(
                        ConversionWarning(
                            "OCR_LAYOUT_LIMITED",
                            "Text is editable and raster images/basic tables are preserved when detectable. Complex vectors, merged cells and exact text flow may differ. OCR is used only for pages without enough extractable text."
                        )
                    )
                },
            )
        } catch (error: ConversionException) {
            throw error
        } catch (error: Exception) {
            throw ConversionException.Io("PDF to DOCX conversion failed", error)
        } finally {
            ocrEngine?.close()
            descriptor.close()
        }
    }

    override suspend fun wordToPdf(
        input: Uri,
        output: Uri,
        options: WordToPdfOptions,
        progress: ConversionProgressListener,
    ): ConversionResult {
        progress.onProgress(ConversionProgress(ConversionStage.VALIDATING, 0, 1))
        try {
            val bytes = appContext.contentResolver.openInputStream(input)?.use { stream ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    coroutineContext.ensureActive()
                    val read = stream.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > MAX_WORD_INPUT_BYTES) {
                        throw ConversionException.LimitExceeded("Word input exceeds ${MAX_WORD_INPUT_BYTES / 1_048_576} MB")
                    }
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            } ?: throw ConversionException.InvalidInput("Cannot open Word input Uri")

            val model = WordParser.parse(bytes)
            coroutineContext.ensureActive()
            progress.onProgress(ConversionProgress(ConversionStage.WRITING, 0, model.blocks.size))
            val pageCount = appContext.contentResolver.openOutputStream(output, "rwt")?.use { stream ->
                WordPdfRenderer.render(model, stream, options.maxPages, progress)
            } ?: throw ConversionException.Io("Cannot open PDF output Uri")
            return ConversionResult(
                output = output,
                pageCount = pageCount,
                warnings = listOf(
                    ConversionWarning(
                        "BASIC_WORD_RENDERER",
                        "Paragraph fonts, inline images and basic tables are rendered. Floating objects, fields, complex pagination and tracked changes may differ from Microsoft Word."
                    )
                ),
            )
        } catch (error: ConversionException) {
            throw error
        } catch (error: Exception) {
            throw ConversionException.Io("Word to PDF conversion failed", error)
        }
    }

    private companion object {
        const val MAX_PAGE_PIXELS = 40_000_000L
        const val MIN_DIRECT_TEXT_CHARS = 10
        const val MAX_WORD_INPUT_BYTES = 100 * 1_048_576
    }

    private fun renderPageGraphics(page: PdfRenderer.Page, dpi: Int): EmbeddedImage {
        val scale = dpi / 72f
        val width = (page.width * scale).toInt().coerceAtLeast(1)
        val height = (page.height * scale).toInt().coerceAtLeast(1)
        val pixelCount = width.toLong() * height.toLong()
        if (pixelCount > MAX_PAGE_PIXELS) {
            throw ConversionException.LimitExceeded("Page needs $pixelCount pixels; maximum is $MAX_PAGE_PIXELS")
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            bitmap.toPageGraphics(page.width, page.height)
        } finally {
            bitmap.recycle()
        }
    }

    private fun Bitmap.toPageGraphics(pageWidth: Int, pageHeight: Int): EmbeddedImage {
        val png = ByteArrayOutputStream().use { bytes ->
            if (!compress(Bitmap.CompressFormat.PNG, 100, bytes)) {
                throw ConversionException.Io("Cannot encode PDF page graphics")
            }
            bytes.toByteArray()
        }
        return EmbeddedImage(png, 0f, 0f, pageWidth.toFloat(), pageHeight.toFloat())
    }
}
