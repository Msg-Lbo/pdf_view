package com.lightread.pdfreader.data.model

data class PdfGroupWithFiles(
    val group: PdfGroup,
    val files: List<PdfFile>,
    val progress: ReadingProgress?
)
