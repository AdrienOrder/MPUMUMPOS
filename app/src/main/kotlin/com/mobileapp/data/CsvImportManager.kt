package com.mobileapp.data

import android.content.Context
import android.os.Environment
import com.mobileapp.LogStorageManager
import java.io.File

object CsvImportManager {
    
    fun getCsvFilesFromDownloads(context: Context): List<ImportFile> {
        val files = mutableListOf<ImportFile>()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        
        if (downloadsDir != null && downloadsDir.exists()) {
            downloadsDir.listFiles()?.filter { it.extension.equals("csv", ignoreCase = true) }?.forEach { file ->
                files.add(ImportFile(
                    name = file.name,
                    path = file.absolutePath,
                    size = file.length(),
                    modifiedAt = file.lastModified()
                ))
            }
        }
        
        LogStorageManager.logMessage("Импорт: ${files.size} CSV файлов")
        return files.sortedByDescending { it.modifiedAt }
    }
    
    fun importCsvFile(context: Context, sourcePath: String, fileName: String): Boolean {
        return try {
            val destDir = File(context.getExternalFilesDir(null), "csv")
            if (!destDir.exists()) {
                destDir.mkdirs()
            }
            
            val destPath = File(destDir, fileName).absolutePath
            File(sourcePath).copyTo(File(destPath), overwrite = true)
            LogStorageManager.logMessage("Импорт: $fileName")
            true
        } catch (e: Exception) {
            LogStorageManager.logMessage("Ошибка импорта: ${e.message}")
            false
        }
    }
}

data class ImportFile(
    val name: String,
    val path: String,
    val size: Long,
    val modifiedAt: Long
)