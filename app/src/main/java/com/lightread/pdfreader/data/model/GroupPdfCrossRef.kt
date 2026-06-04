package com.lightread.pdfreader.data.model

data class GroupPdfCrossRef(
    val crossId: Long,
    val groupId: String,
    val pdfId: String,
    val sortOrder: Int
)
