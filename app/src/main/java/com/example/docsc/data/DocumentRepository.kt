package com.example.docsc.data

import android.content.Context
import com.example.docsc.model.DocCategory
import com.example.docsc.model.FilterType
import com.example.docsc.model.PageItem
import com.example.docsc.model.ScannedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class DocumentRepository(private val context: Context) {

    private val _documents = MutableStateFlow<List<ScannedDocument>>(emptyList())
    val documents: StateFlow<List<ScannedDocument>> = _documents.asStateFlow()

    private val _vaultPin = MutableStateFlow("1234")
    val vaultPin: StateFlow<String> = _vaultPin.asStateFlow()

    private val storageFile = File(context.filesDir, "documents_db.json")

    suspend fun loadDocuments() = withContext(Dispatchers.IO) {
        if (!storageFile.exists()) {
            // Seed with sample initial document
            val initialDocs = createSampleDocuments()
            _documents.value = initialDocs
            saveToDisk(initialDocs)
            return@withContext
        }

        try {
            val jsonStr = storageFile.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<ScannedDocument>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val title = obj.optString("title", "Untitled Document")
                val categoryName = obj.optString("category", DocCategory.OTHER.name)
                val category = try { DocCategory.valueOf(categoryName) } catch (e: Exception) { DocCategory.OTHER }
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val isVault = obj.optBoolean("isVault", false)
                val fileSize = obj.optString("fileSize", "150 KB")

                val pagesArray = obj.optJSONArray("pages") ?: JSONArray()
                val pages = mutableListOf<PageItem>()
                for (j in 0 until pagesArray.length()) {
                    val pObj = pagesArray.getJSONObject(j)
                    val pId = pObj.optString("id", UUID.randomUUID().toString())
                    val pPath = pObj.optString("imagePath", "")
                    val pRotation = pObj.optDouble("rotation", 0.0).toFloat()
                    val pFilterName = pObj.optString("filter", FilterType.DOC_SCAN.name)
                    val pFilter = try { FilterType.valueOf(pFilterName) } catch (e: Exception) { FilterType.DOC_SCAN }
                    pages.add(PageItem(pId, pPath, pRotation, pFilter))
                }

                list.add(
                    ScannedDocument(
                        id = id,
                        title = title,
                        category = category,
                        createdAtMillis = createdAt,
                        pages = pages,
                        isVault = isVault,
                        fileSizeFormatted = fileSize
                    )
                )
            }
            _documents.value = list
        } catch (e: Exception) {
            e.printStackTrace()
            _documents.value = createSampleDocuments()
        }
    }

    private fun createSampleDocuments(): List<ScannedDocument> {
        return listOf(
            ScannedDocument(
                id = "sample-1",
                title = "Cloud Service Receipt",
                category = DocCategory.RECEIPT,
                createdAtMillis = System.currentTimeMillis() - 86400000L * 2,
                pages = listOf(
                    PageItem(
                        id = "p1",
                        imagePath = "android.resource://${context.packageName}/drawable/hero_scanner",
                        rotationDegrees = 0f,
                        filterType = FilterType.DOC_SCAN
                    )
                ),
                isVault = false,
                fileSizeFormatted = "240 KB"
            ),
            ScannedDocument(
                id = "sample-2",
                title = "Confidential NDA Contract",
                category = DocCategory.CONTRACT,
                createdAtMillis = System.currentTimeMillis() - 86400000L * 4,
                pages = listOf(
                    PageItem(
                        id = "p2",
                        imagePath = "android.resource://${context.packageName}/drawable/hero_scanner",
                        rotationDegrees = 0f,
                        filterType = FilterType.ENHANCED
                    )
                ),
                isVault = true,
                fileSizeFormatted = "320 KB"
            )
        )
    }

    suspend fun saveDocument(doc: ScannedDocument) = withContext(Dispatchers.IO) {
        val current = _documents.value.toMutableList()
        val index = current.indexOfFirst { it.id == doc.id }
        if (index >= 0) {
            current[index] = doc
        } else {
            current.add(0, doc)
        }
        _documents.value = current
        saveToDisk(current)
    }

    suspend fun deleteDocument(docId: String) = withContext(Dispatchers.IO) {
        val current = _documents.value.filterNot { it.id == docId }
        _documents.value = current
        saveToDisk(current)
    }

    suspend fun toggleVault(docId: String) = withContext(Dispatchers.IO) {
        val current = _documents.value.map {
            if (it.id == docId) it.copy(isVault = !it.isVault) else it
        }
        _documents.value = current
        saveToDisk(current)
    }

    private fun saveToDisk(list: List<ScannedDocument>) {
        try {
            val array = JSONArray()
            list.forEach { doc ->
                val obj = JSONObject()
                obj.put("id", doc.id)
                obj.put("title", doc.title)
                obj.put("category", doc.category.name)
                obj.put("createdAt", doc.createdAtMillis)
                obj.put("isVault", doc.isVault)
                obj.put("fileSize", doc.fileSizeFormatted)

                val pagesArray = JSONArray()
                doc.pages.forEach { page ->
                    val pObj = JSONObject()
                    pObj.put("id", page.id)
                    pObj.put("imagePath", page.imagePath)
                    pObj.put("rotation", page.rotationDegrees.toDouble())
                    pObj.put("filter", page.filterType.name)
                    pagesArray.put(pObj)
                }
                obj.put("pages", pagesArray)
                array.put(obj)
            }
            storageFile.writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
