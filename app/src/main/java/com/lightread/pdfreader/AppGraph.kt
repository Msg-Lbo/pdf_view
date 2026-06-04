package com.lightread.pdfreader

import android.content.Context
import com.lightread.pdfreader.data.PdfLibraryStore
import com.lightread.pdfreader.data.PdfScanner

object AppGraph {
    lateinit var store: PdfLibraryStore
        private set
    lateinit var scanner: PdfScanner
        private set

    fun init(context: Context) {
        if (::store.isInitialized && ::scanner.isInitialized) return
        val appContext = context.applicationContext
        store = PdfLibraryStore(appContext)
        scanner = PdfScanner(appContext)
    }
}
