package com.lightread.pdfreader.data.model

data class ReadingProgress(
    val groupId: String,
    val currentPdfId: String,
    val currentPage: Int,
    val pageScrollOffset: Float,
    val updateTime: Long
)
