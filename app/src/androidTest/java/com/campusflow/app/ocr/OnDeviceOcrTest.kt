package com.campusflow.app.ocr

import android.graphics.*
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.campusflow.app.CampusApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OnDeviceOcrTest {
    @Test fun bundledOcrReadsImageWithoutSavingActivities() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CampusApp>()
        val before = app.repository.activities.first().size
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 50f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        listOf("Programación II", "Viernes", "08:00 - 11:40", "Inglés III", "Sábado", "08:00 - 11:40").forEachIndexed { index, line -> canvas.drawText(line, 70f, 90f + index * 95, paint) }
        val file = File(app.cacheDir, "ocr-test.png")
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val document = OnDeviceOcr(app).recognize(Uri.fromFile(file))
            assertTrue(document.text.contains("Program", ignoreCase = true))
            val result = HeuristicScheduleInterpreter().interpret(document)
            assertEquals(2, result.size)
            assertTrue(result.any { it.weekday == 5 && it.startMinute == 480 && it.endMinute == 700 })
            assertEquals(before, app.repository.activities.first().size)
            val permissions = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
            assertFalse(permissions.contains(android.Manifest.permission.INTERNET))
        } finally { file.delete(); bitmap.recycle() }
    }
}
