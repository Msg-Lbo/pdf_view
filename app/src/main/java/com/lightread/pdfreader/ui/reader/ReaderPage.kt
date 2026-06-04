package com.lightread.pdfreader.ui.reader

import com.lightread.pdfreader.data.model.PdfFile

data class ReaderPage(
    val pdfFile: PdfFile,
    val pageIndex: Int,
    val globalPageNumber: Int,
    val globalPageCount: Int,
    val isFirstPageOfPdf: Boolean,
    val isRenderable: Boolean
)
