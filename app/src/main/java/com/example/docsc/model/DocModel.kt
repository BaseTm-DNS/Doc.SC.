package com.example.docsc.model

enum class DocCategory(val displayName: String, val iconName: String) {
    ALL("All", "Folder"),
    RECEIPT("Receipts", "Receipt"),
    CONTRACT("Contracts", "Description"),
    ID_CARD("IDs & Cards", "Badge"),
    NOTE("Notes", "EditNote"),
    CERTIFICATE("Certificates", "Verified"),
    OTHER("Other", "FilePresent")
}

enum class FilterType(val displayName: String) {
    ORIGINAL("Original"),
    DOC_SCAN("Doc Scan"),
    ENHANCED("Enhanced"),
    GRAYSCALE("B&W / Gray")
}

data class PageItem(
    val id: String,
    val imagePath: String,
    val rotationDegrees: Float = 0f,
    val filterType: FilterType = FilterType.DOC_SCAN
)

data class ScannedDocument(
    val id: String,
    val title: String,
    val category: DocCategory = DocCategory.OTHER,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val pages: List<PageItem> = emptyList(),
    val isVault: Boolean = false,
    val tags: List<String> = emptyList(),
    val fileSizeFormatted: String = "120 KB"
)

data class DnsServer(
    val name: String,
    val ip: String,
    val provider: String,
    val isDohSupported: Boolean,
    val securityFeature: String
)

data class DnsCheckResult(
    val server: DnsServer,
    val latencyMs: Long,
    val isSuccessful: Boolean,
    val resolvedIp: String,
    val securityStatus: String
)
