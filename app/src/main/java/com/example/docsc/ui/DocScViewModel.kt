package com.example.docsc.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.docsc.data.DnsService
import com.example.docsc.data.DocumentRepository
import com.example.docsc.model.DnsCheckResult
import com.example.docsc.model.DocCategory
import com.example.docsc.model.FilterType
import com.example.docsc.model.PageItem
import com.example.docsc.model.ScannedDocument
import com.example.docsc.util.ImageProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class AppScreen {
    HOME,
    CAMERA_SCAN,
    SCAN_EDITOR,
    DOCUMENT_DETAIL,
    VAULT,
    DNS_INSPECTOR
}

class DocScViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(application)

    val documents: StateFlow<List<ScannedDocument>> = repository.documents

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Navigation backstack support
    private val screenHistory = mutableListOf<AppScreen>()

    // Filter & Search in Home
    private val _selectedCategory = MutableStateFlow(DocCategory.ALL)
    val selectedCategory: StateFlow<DocCategory> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Active Scan / Edit session
    private val _activePages = MutableStateFlow<List<PageItem>>(emptyList())
    val activePages: StateFlow<List<PageItem>> = _activePages.asStateFlow()

    private val _activePageIndex = MutableStateFlow(0)
    val activePageIndex: StateFlow<Int> = _activePageIndex.asStateFlow()

    private val _activeDocTitle = MutableStateFlow("Doc_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}")
    val activeDocTitle: StateFlow<String> = _activeDocTitle.asStateFlow()

    private val _activeDocCategory = MutableStateFlow(DocCategory.RECEIPT)
    val activeDocCategory: StateFlow<DocCategory> = _activeDocCategory.asStateFlow()

    // Selected document for detail view
    private val _selectedDocument = MutableStateFlow<ScannedDocument?>(null)
    val selectedDocument: StateFlow<ScannedDocument?> = _selectedDocument.asStateFlow()

    // Camera settings & state
    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_OFF)
    val flashMode: StateFlow<Int> = _flashMode.asStateFlow()

    private val _lensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing: StateFlow<Int> = _lensFacing.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _cameraErrorMessage = MutableStateFlow<String?>(null)
    val cameraErrorMessage: StateFlow<String?> = _cameraErrorMessage.asStateFlow()

    // System Camera URI placeholder
    var pendingSystemCameraUri: Uri? = null

    // Vault PIN state
    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    // DNS Inspector state
    private val _dnsResults = MutableStateFlow<List<DnsCheckResult>>(emptyList())
    val dnsResults: StateFlow<List<DnsCheckResult>> = _dnsResults.asStateFlow()

    private val _isDnsRunning = MutableStateFlow(false)
    val isDnsRunning: StateFlow<Boolean> = _isDnsRunning.asStateFlow()

    // PDF Export Progress
    private val _isExportingPdf = MutableStateFlow(false)
    val isExportingPdf: StateFlow<Boolean> = _isExportingPdf.asStateFlow()

    init {
        viewModelScope.launch {
            repository.loadDocuments()
        }
    }

    fun navigateTo(screen: AppScreen) {
        if (_currentScreen.value != screen) {
            screenHistory.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (screenHistory.isNotEmpty()) {
            _currentScreen.value = screenHistory.removeAt(screenHistory.lastIndex)
            return true
        }
        if (_currentScreen.value != AppScreen.HOME) {
            _currentScreen.value = AppScreen.HOME
            return true
        }
        return false
    }

    fun setSelectedCategory(category: DocCategory) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun startNewScan() {
        _activePages.value = emptyList()
        _activePageIndex.value = 0
        _activeDocTitle.value = "Doc_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}"
        _activeDocCategory.value = DocCategory.RECEIPT
        _cameraErrorMessage.value = null
        navigateTo(AppScreen.CAMERA_SCAN)
    }

    fun toggleFlash() {
        _flashMode.value = when (_flashMode.value) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    fun switchCamera() {
        _lensFacing.value = if (_lensFacing.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    fun setCameraError(error: String?) {
        _cameraErrorMessage.value = error
    }

    fun onImageCaptured(imagePath: String) {
        val newPage = PageItem(
            id = UUID.randomUUID().toString(),
            imagePath = imagePath,
            rotationDegrees = 0f,
            filterType = FilterType.DOC_SCAN
        )
        val updated = _activePages.value + newPage
        _activePages.value = updated
        _activePageIndex.value = updated.lastIndex
        navigateTo(AppScreen.SCAN_EDITOR)
    }

    fun onGalleryImageSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val permanentFile = ImageProcessor.createPermanentImageFile(context)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(permanentFile).use { output ->
                        input.copyTo(output)
                    }
                }
                onImageCaptured(permanentFile.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(getApplication(), "Failed to import image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun prepareSystemCameraUri(): Uri {
        val uri = ImageProcessor.createTempImageUri(getApplication())
        pendingSystemCameraUri = uri
        return uri
    }

    fun onSystemCameraSuccess() {
        val uri = pendingSystemCameraUri ?: return
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val permanentFile = ImageProcessor.createPermanentImageFile(context)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(permanentFile).use { output ->
                        input.copyTo(output)
                    }
                }
                onImageCaptured(permanentFile.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
                onImageCaptured(uri.toString())
            }
        }
    }

    fun setActivePageIndex(index: Int) {
        if (index in _activePages.value.indices) {
            _activePageIndex.value = index
        }
    }

    fun updateCurrentPageFilter(filter: FilterType) {
        val index = _activePageIndex.value
        val pages = _activePages.value.toMutableList()
        if (index in pages.indices) {
            pages[index] = pages[index].copy(filterType = filter)
            _activePages.value = pages
        }
    }

    fun rotateCurrentPage() {
        val index = _activePageIndex.value
        val pages = _activePages.value.toMutableList()
        if (index in pages.indices) {
            val newRot = (pages[index].rotationDegrees + 90f) % 360f
            pages[index] = pages[index].copy(rotationDegrees = newRot)
            _activePages.value = pages
        }
    }

    fun deleteCurrentPage() {
        val pages = _activePages.value.toMutableList()
        val index = _activePageIndex.value
        if (index in pages.indices) {
            pages.removeAt(index)
            _activePages.value = pages
            if (pages.isEmpty()) {
                navigateTo(AppScreen.CAMERA_SCAN)
            } else {
                _activePageIndex.value = (_activePageIndex.value - 1).coerceAtLeast(0)
            }
        }
    }

    fun setActiveDocTitle(title: String) {
        _activeDocTitle.value = title
    }

    fun setActiveDocCategory(category: DocCategory) {
        _activeDocCategory.value = category
    }

    fun saveActiveDocument() {
        val pages = _activePages.value
        if (pages.isEmpty()) return

        viewModelScope.launch {
            val doc = ScannedDocument(
                id = UUID.randomUUID().toString(),
                title = _activeDocTitle.value.ifBlank { "Untitled Document" },
                category = _activeDocCategory.value,
                createdAtMillis = System.currentTimeMillis(),
                pages = pages,
                isVault = false,
                fileSizeFormatted = "${(pages.size * 180 + 40)} KB"
            )
            repository.saveDocument(doc)
            _selectedDocument.value = doc
            navigateTo(AppScreen.DOCUMENT_DETAIL)
            Toast.makeText(getApplication(), "Document saved successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    fun openDocumentDetail(doc: ScannedDocument) {
        _selectedDocument.value = doc
        navigateTo(AppScreen.DOCUMENT_DETAIL)
    }

    fun deleteDocument(doc: ScannedDocument) {
        viewModelScope.launch {
            repository.deleteDocument(doc.id)
            if (_selectedDocument.value?.id == doc.id) {
                _selectedDocument.value = null
                navigateTo(AppScreen.HOME)
            }
            Toast.makeText(getApplication(), "Document deleted", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleDocumentVault(doc: ScannedDocument) {
        viewModelScope.launch {
            repository.toggleVault(doc.id)
            _selectedDocument.value = _selectedDocument.value?.let {
                if (it.id == doc.id) it.copy(isVault = !it.isVault) else it
            }
            val status = if (!doc.isVault) "moved to Secure Vault" else "moved to Standard Library"
            Toast.makeText(getApplication(), "Document $status", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareDocumentPdf(context: Context, doc: ScannedDocument) {
        viewModelScope.launch {
            _isExportingPdf.value = true
            val pdfFile = ImageProcessor.generatePdf(context, doc.pages, doc.title)
            _isExportingPdf.value = false

            if (pdfFile != null && pdfFile.exists()) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    pdfFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, doc.title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Document PDF"))
            } else {
                Toast.makeText(context, "Could not generate PDF for sharing", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun unlockVault(pin: String): Boolean {
        return if (pin == repository.vaultPin.value) {
            _isVaultUnlocked.value = true
            true
        } else {
            false
        }
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
    }

    fun runDnsSecurityCheck() {
        viewModelScope.launch {
            _isDnsRunning.value = true
            _dnsResults.value = emptyList()
            val results = mutableListOf<DnsCheckResult>()
            for (server in DnsService.popularDnsServers) {
                val result = DnsService.testDnsServer(server)
                results.add(result)
                _dnsResults.value = results.toList()
            }
            _isDnsRunning.value = false
        }
    }
}
