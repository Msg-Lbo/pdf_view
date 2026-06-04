package com.lightread.pdfreader.data

import android.content.Context
import com.lightread.pdfreader.data.model.GroupPdfCrossRef
import com.lightread.pdfreader.data.model.PdfFile
import com.lightread.pdfreader.data.model.PdfGroup
import com.lightread.pdfreader.data.model.PdfGroupWithFiles
import com.lightread.pdfreader.data.model.ReadingProgress
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PdfLibraryStore(private val context: Context) {
    private val pdfFiles = linkedMapOf<String, PdfFile>()
    private val groups = linkedMapOf<String, PdfGroup>()
    private val refs = mutableListOf<GroupPdfCrossRef>()
    private val progressByGroup = linkedMapOf<String, ReadingProgress>()
    private val storeFileName = "library_store.json"

    init {
        load()
    }

    @Synchronized
    fun upsertPdfFiles(files: List<PdfFile>): Int {
        var changed = 0
        files.forEach { file ->
            if (pdfFiles[file.id] != file) {
                pdfFiles[file.id] = file
                changed++
            }
        }
        if (changed > 0) save()
        return changed
    }

    @Synchronized
    fun getPdfFiles(): List<PdfFile> {
        return pdfFiles.values.sortedNaturally()
    }

    @Synchronized
    fun createGroupFromPdfIds(pdfIds: List<String>): PdfGroup? {
        val files = pdfIds.mapNotNull { pdfFiles[it] }
            .sortedNaturally()
        if (files.isEmpty()) return null

        val now = System.currentTimeMillis()
        val groupId = UUID.randomUUID().toString()
        val group = PdfGroup(
            groupId = groupId,
            title = files.first().fileName.removeSuffix(".pdf").removeSuffix(".PDF"),
            coverPath = null,
            createdTime = now
        )
        groups[groupId] = group
        files.forEachIndexed { index, file ->
            refs += GroupPdfCrossRef(
                crossId = now + index,
                groupId = groupId,
                pdfId = file.id,
                sortOrder = index
            )
        }
        save()
        return group
    }

    @Synchronized
    fun getGroupsWithFiles(): List<PdfGroupWithFiles> {
        return groups.values.map { group ->
            group.toGroupWithFiles()
        }.sortedByDescending { groupWithFiles ->
            groupWithFiles.progress?.updateTime ?: groupWithFiles.group.createdTime
        }
    }

    @Synchronized
    fun getGroupWithFiles(groupId: String): PdfGroupWithFiles? {
        return groups[groupId]?.toGroupWithFiles()
    }

    @Synchronized
    fun saveProgress(progress: ReadingProgress) {
        progressByGroup[progress.groupId] = progress
        save()
    }

    @Synchronized
    fun getProgress(groupId: String): ReadingProgress? {
        return progressByGroup[groupId]
    }

    private fun PdfGroup.toGroupWithFiles(): PdfGroupWithFiles {
        val files = refs.asSequence()
            .filter { ref -> ref.groupId == groupId }
            .sortedBy { ref -> ref.sortOrder }
            .mapNotNull { ref -> pdfFiles[ref.pdfId] }
            .toList()
        return PdfGroupWithFiles(
            group = this,
            files = files,
            progress = progressByGroup[groupId]
        )
    }

    private fun load() {
        runCatching {
            val json = context.openFileInput(storeFileName).bufferedReader().use { reader -> reader.readText() }
            val root = JSONObject(json)
            root.optJSONArray("pdfFiles")?.forEachObject { objectJson ->
                val file = PdfFile(
                    id = objectJson.getString("id"),
                    fileName = objectJson.getString("fileName"),
                    filePath = objectJson.getString("filePath"),
                    pageCount = objectJson.optInt("pageCount", 0),
                    addedTime = objectJson.optLong("addedTime", 0L),
                    sizeBytes = objectJson.optLong("sizeBytes", 0L)
                )
                pdfFiles[file.id] = file
            }
            root.optJSONArray("groups")?.forEachObject { objectJson ->
                val group = PdfGroup(
                    groupId = objectJson.getString("groupId"),
                    title = objectJson.getString("title"),
                    coverPath = objectJson.optString("coverPath").ifBlank { null },
                    createdTime = objectJson.optLong("createdTime", 0L)
                )
                groups[group.groupId] = group
            }
            root.optJSONArray("refs")?.forEachObject { objectJson ->
                refs += GroupPdfCrossRef(
                    crossId = objectJson.optLong("crossId", 0L),
                    groupId = objectJson.getString("groupId"),
                    pdfId = objectJson.getString("pdfId"),
                    sortOrder = objectJson.optInt("sortOrder", 0)
                )
            }
            root.optJSONArray("progress")?.forEachObject { objectJson ->
                val progress = ReadingProgress(
                    groupId = objectJson.getString("groupId"),
                    currentPdfId = objectJson.getString("currentPdfId"),
                    currentPage = objectJson.optInt("currentPage", 0),
                    pageScrollOffset = objectJson.optDouble("pageScrollOffset", 0.0).toFloat(),
                    updateTime = objectJson.optLong("updateTime", 0L)
                )
                progressByGroup[progress.groupId] = progress
            }
        }
    }

    private fun save() {
        val root = JSONObject()
            .put("pdfFiles", JSONArray().also { array -> pdfFiles.values.forEach { array.put(it.toJson()) } })
            .put("groups", JSONArray().also { array -> groups.values.forEach { array.put(it.toJson()) } })
            .put("refs", JSONArray().also { array -> refs.forEach { array.put(it.toJson()) } })
            .put("progress", JSONArray().also { array -> progressByGroup.values.forEach { array.put(it.toJson()) } })
        context.openFileOutput(storeFileName, Context.MODE_PRIVATE).bufferedWriter().use { writer ->
            writer.write(root.toString())
        }
    }

    private fun PdfFile.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("fileName", fileName)
            .put("filePath", filePath)
            .put("pageCount", pageCount)
            .put("addedTime", addedTime)
            .put("sizeBytes", sizeBytes)
    }

    private fun PdfGroup.toJson(): JSONObject {
        return JSONObject()
            .put("groupId", groupId)
            .put("title", title)
            .put("coverPath", coverPath ?: "")
            .put("createdTime", createdTime)
    }

    private fun GroupPdfCrossRef.toJson(): JSONObject {
        return JSONObject()
            .put("crossId", crossId)
            .put("groupId", groupId)
            .put("pdfId", pdfId)
            .put("sortOrder", sortOrder)
    }

    private fun ReadingProgress.toJson(): JSONObject {
        return JSONObject()
            .put("groupId", groupId)
            .put("currentPdfId", currentPdfId)
            .put("currentPage", currentPage)
            .put("pageScrollOffset", pageScrollOffset)
            .put("updateTime", updateTime)
    }

    private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(block)
        }
    }

    private fun Iterable<PdfFile>.sortedNaturally(): List<PdfFile> {
        return sortedWith { left, right -> NaturalFileNameComparator.compare(left.fileName, right.fileName) }
    }
}
