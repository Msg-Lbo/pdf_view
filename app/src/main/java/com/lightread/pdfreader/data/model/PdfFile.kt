package com.lightread.pdfreader.data.model

data class PdfFile(
    val id: String,
    val fileName: String,
    val filePath: String,
    val pageCount: Int,
    val addedTime: Long,
    val sizeBytes: Long
)
