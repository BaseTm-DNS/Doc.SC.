package com.example.docsc.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.docsc.model.FilterType
import com.example.docsc.model.PageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object ImageProcessor {

    fun createTempImageUri(context: Context): Uri {
        val tempDir = File(context.cacheDir, "camera_captures")
        if (!tempDir.exists()) tempDir.mkdirs()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tempFile = File(tempDir, "DOC_${timeStamp}_${UUID.randomUUID().toString().take(6)}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    fun createPermanentImageFile(context: Context): File {
        val docsDir = File(context.filesDir, "scanned_images")
        if (!docsDir.exists()) docsDir.mkdirs()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(docsDir, "PAGE_${timeStamp}_${UUID.randomUUID().toString().take(6)}.jpg")
    }

    suspend fun loadAndProcessBitmap(
        context: Context,
        imagePath: String,
        rotationDegrees: Float,
        filterType: FilterType,
        maxDim: Int = 1600
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = File(imagePath)
            val bitmap = if (file.exists()) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                
                var inSampleSize = 1
                while (options.outWidth / inSampleSize > maxDim || options.outHeight / inSampleSize > maxDim) {
                    inSampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = inSampleSize }
                BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            } else if (imagePath.startsWith("content://")) {
                val uri = Uri.parse(imagePath)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                null
            } ?: return@withContext null

            // Apply rotation
            val rotated = if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            // Apply filter
            applyFilter(rotated, filterType)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun applyFilter(source: Bitmap, filterType: FilterType): Bitmap {
        return when (filterType) {
            FilterType.ORIGINAL -> source
            FilterType.GRAYSCALE -> {
                val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint()
                val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                canvas.drawBitmap(source, 0f, 0f, paint)
                output
            }
            FilterType.DOC_SCAN -> {
                // High contrast document filter - cleans background & sharpens text
                val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint()
                val colorMatrix = ColorMatrix(
                    floatArrayOf(
                        1.5f, 0f, 0f, 0f, -60f,
                        0f, 1.5f, 0f, 0f, -60f,
                        0f, 0f, 1.5f, 0f, -60f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                canvas.drawBitmap(source, 0f, 0f, paint)
                output
            }
            FilterType.ENHANCED -> {
                // Enhanced color filter - punchy saturation and brightness
                val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint()
                val colorMatrix = ColorMatrix()
                colorMatrix.setSaturation(1.4f)
                val scale = 1.15f
                val translate = 5f
                val contrastMatrix = ColorMatrix(
                    floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                colorMatrix.postConcat(contrastMatrix)
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                canvas.drawBitmap(source, 0f, 0f, paint)
                output
            }
        }
    }

    suspend fun generatePdf(
        context: Context,
        pages: List<PageItem>,
        docTitle: String
    ): File? = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext null
        try {
            val pdfDocument = PdfDocument()
            val pdfDir = File(context.cacheDir, "pdf_exports")
            if (!pdfDir.exists()) pdfDir.mkdirs()
            val sanitized = docTitle.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val pdfFile = File(pdfDir, "${sanitized}_${System.currentTimeMillis()}.pdf")

            // Standard A4 dimensions in points (72 points/inch): 595 x 842
            val pageWidth = 595
            val pageHeight = 842

            pages.forEachIndexed { index, pageItem ->
                val processedBitmap = loadAndProcessBitmap(
                    context = context,
                    imagePath = pageItem.imagePath,
                    rotationDegrees = pageItem.rotationDegrees,
                    filterType = pageItem.filterType,
                    maxDim = 1200
                )

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas

                if (processedBitmap != null) {
                    val margin = 20f
                    val usableWidth = pageWidth - (margin * 2)
                    val usableHeight = pageHeight - (margin * 2)

                    val bmpRatio = processedBitmap.width.toFloat() / processedBitmap.height.toFloat()
                    val pageRatio = usableWidth / usableHeight

                    val drawWidth: Float
                    val drawHeight: Float
                    if (bmpRatio > pageRatio) {
                        drawWidth = usableWidth
                        drawHeight = usableWidth / bmpRatio
                    } else {
                        drawHeight = usableHeight
                        drawWidth = usableHeight * bmpRatio
                    }

                    val left = margin + (usableWidth - drawWidth) / 2f
                    val top = margin + (usableHeight - drawHeight) / 2f

                    val destRect = android.graphics.RectF(left, top, left + drawWidth, top + drawHeight)
                    canvas.drawBitmap(processedBitmap, null, destRect, null)
                }

                pdfDocument.finishPage(pdfPage)
            }

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
