package com.lightread.pdfreader.data.model

data class PdfGroup(
    val groupId: String,
    val title: String,
    val coverPath: String?,
    val createdTime: Long
)
