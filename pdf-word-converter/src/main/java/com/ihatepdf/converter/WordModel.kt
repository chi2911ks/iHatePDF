package com.ihatepdf.converter

internal data class WordRun(
    val text: String,
    val fontFamily: String? = null,
    val fontSizePt: Float = 11f,
    val bold: Boolean = false,
    val italic: Boolean = false,
)

internal sealed interface WordBlock {
    data class Paragraph(val runs: List<WordRun>) : WordBlock
    data class Image(val bytes: ByteArray) : WordBlock
    data class Table(val rows: List<List<String>>) : WordBlock
}

internal data class WordModel(val blocks: List<WordBlock>)
