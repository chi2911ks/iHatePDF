package com.ihatepdf

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ihatepdf.converter.AndroidDocumentConverter
import com.ihatepdf.converter.PdfToDocxMode
import com.ihatepdf.converter.PdfToDocxOptions
import com.ihatepdf.converter.WordToPdfOptions
import com.ihatepdf.ui.theme.IHatePDFTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IHatePDFTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    ConverterScreen(this, Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun ConverterScreen(activity: ComponentActivity, modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf<Uri?>(null) }
    var operation by remember { mutableStateOf(ConversionOperation.PDF_TO_WORD) }
    var mode by remember { mutableStateOf(PdfToDocxMode.EDITABLE) }
    var status by remember { mutableStateOf("Select a PDF to start") }
    var progress by remember { mutableFloatStateOf(0f) }
    var converting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val converter = remember { AndroidDocumentConverter(activity.applicationContext) }

    val startConversion: (Uri?) -> Unit = { output ->
        val source = input
        if (source != null && output != null) {
            converting = true
            status = "Starting conversion"
            progress = 0f
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val listener = com.ihatepdf.converter.ConversionProgressListener { update ->
                        activity.runOnUiThread {
                            progress = update.fraction.coerceIn(0f, 1f)
                            status = "${update.stage}: ${update.completedUnits}/${update.totalUnits}"
                        }
                    }
                    if (operation == ConversionOperation.PDF_TO_WORD) {
                        converter.pdfToDocx(source, output, PdfToDocxOptions(mode = mode), listener)
                    } else {
                        converter.wordToPdf(source, output, WordToPdfOptions(), listener)
                    }
                }.onSuccess { result ->
                    activity.runOnUiThread {
                        converting = false
                        progress = 1f
                        status = "Done: ${result.pageCount} pages" +
                            result.warnings.joinToString(prefix = "\n", separator = "\n") { it.message }
                    }
                }.onFailure { error ->
                    activity.runOnUiThread {
                        converting = false
                        val details = generateSequence(error as Throwable?) { it.cause }
                            .mapNotNull { it.message ?: it.javaClass.simpleName }
                            .distinct()
                            .joinToString(" → ")
                        status = "Failed: $details"
                    }
                }
            }
        }
    }

    val createDocx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DOCX_MIME), startConversion)
    val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME), startConversion)

    val selectInput = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        input = uri
        val kind = if (operation == ConversionOperation.PDF_TO_WORD) "PDF" else "Word"
        status = if (uri == null) "No $kind selected" else "Ready: ${uri.lastPathSegment ?: kind}"
        progress = 0f
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Offline document converter", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        ModeRow("PDF to Word", ConversionOperation.PDF_TO_WORD, operation, !converting) {
            operation = it; input = null; status = "Select a PDF to start"
        }
        ModeRow("Word to PDF", ConversionOperation.WORD_TO_PDF, operation, !converting) {
            operation = it; input = null; status = "Select a DOCX or DOC to start"
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            val types = if (operation == ConversionOperation.PDF_TO_WORD) arrayOf(PDF_MIME)
            else arrayOf(DOCX_MIME, DOC_MIME)
            selectInput.launch(types)
        }, enabled = !converting) {
            val kind = if (operation == ConversionOperation.PDF_TO_WORD) "PDF" else "Word"
            Text(if (input == null) "Select $kind" else "Change $kind")
        }
        Spacer(Modifier.height(16.dp))
        if (operation == ConversionOperation.PDF_TO_WORD) {
            ModeRow("Editable (text + OCR)", PdfToDocxMode.EDITABLE, mode, !converting) { mode = it }
            ModeRow("Visual (page images)", PdfToDocxMode.VISUAL, mode, !converting) { mode = it }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (operation == ConversionOperation.PDF_TO_WORD) createDocx.launch(defaultOutputName(input, "docx"))
                else createPdf.launch(defaultOutputName(input, "pdf"))
            },
            enabled = input != null && !converting,
        ) { Text(if (operation == ConversionOperation.PDF_TO_WORD) "Convert to DOCX" else "Convert to PDF") }
        Spacer(Modifier.height(20.dp))
        if (converting) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ModeRow(
    label: String,
    value: PdfToDocxMode,
    selected: PdfToDocxMode,
    enabled: Boolean,
    onSelected: (PdfToDocxMode) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = value == selected, onClick = { onSelected(value) }, enabled = enabled)
        Text(label)
    }
}

@Composable
private fun ModeRow(
    label: String,
    value: ConversionOperation,
    selected: ConversionOperation,
    enabled: Boolean,
    onSelected: (ConversionOperation) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = value == selected, onClick = { onSelected(value) }, enabled = enabled)
        Text(label)
    }
}

private fun defaultOutputName(input: Uri?, extension: String): String {
    val source = input?.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "converted"
    return "$source.$extension"
}

private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private const val DOC_MIME = "application/msword"
private const val PDF_MIME = "application/pdf"
private enum class ConversionOperation { PDF_TO_WORD, WORD_TO_PDF }
