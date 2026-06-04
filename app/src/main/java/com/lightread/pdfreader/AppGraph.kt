package com.lightread.pdfreader

import android.content.Context
import com.lightread.pdfreader.data.PdfLibraryStore
import com.lightread.pdfreader.data.PdfScanner
import com.lightread.pdfreader.data.RcloneSourceClient
import com.lightread.pdfreader.data.UpdateChecker

object AppGraph {
    lateinit var store: PdfLibraryStore
        private set
    lateinit var scanner: PdfScanner
        private set
    lateinit var rcloneClient: RcloneSourceClient
        private set
    lateinit var updateChecker: UpdateChecker
        private set

    fun init(context: Context) {
        if (::store.isInitialized && ::scanner.isInitialized && ::rcloneClient.isInitialized && ::updateChecker.isInitialized) return
        val appContext = context.applicationContext
        store = PdfLibraryStore(appContext)
        scanner = PdfScanner(appContext)
        rcloneClient = RcloneSourceClient(appContext)
        updateChecker = UpdateChecker(appContext)
    }
}
