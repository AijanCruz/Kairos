package com.campusflow.app.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class OnDeviceOcr(private val context: Context) {
    suspend fun recognize(uri: Uri): RecognizedDocument = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "No se pudo leer esta imagen. Prueba con una captura JPG o PNG." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = requireNotNull(context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }) { "Imagen no disponible." }
        val orientation = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        val matrix = Matrix().apply {
            if (orientation?.isFlipped == true) postScale(-1f, 1f)
            postRotate((orientation?.rotationDegrees ?: 0).toFloat())
        }
        val oriented = if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = recognizer.process(InputImage.fromBitmap(oriented, 0)).await()
            val lines = result.textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
                line.boundingBox?.let { TextRegion(line.text, it.left, it.top, it.right, it.bottom) }
            }
            val words = result.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { word ->
                word.boundingBox?.let { TextRegion(word.text, it.left, it.top, it.right, it.bottom) }
            }
            RecognizedDocument(result.text, lines, words)
        } finally { recognizer.close() }
    }
}
