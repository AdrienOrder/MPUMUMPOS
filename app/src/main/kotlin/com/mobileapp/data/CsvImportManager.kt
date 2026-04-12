package com.mobileapp.data

import android.content.Context
import android.os.Environment
import com.mobileapp.LogStorageManager
import java.io.File

object CsvImportManager {
    
    fun getCsvFilesFromDownloads(context: Context): List<ImportFile> {
        LogStorageManager.logMessage("Импорт: Поиск CSV файлов в Загрузках")
        val files = mutableListOf<ImportFile>()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        
        if (downloadsDir != null && downloadsDir.exists()) {
            LogStorageManager.logMessage("Импорт: Папка Загрузки: ${downloadsDir.absolutePath}")
            downloadsDir.listFiles()?.filter { it.extension.equals("csv", ignoreCase = true) }?.forEach { file ->
                LogStorageManager.logMessage("Импорт: Найден файл: ${file.name}, размер: ${file.length()} байт")
                files.add(ImportFile(
                    name = file.name,
                    path = file.absolutePath,
                    size = file.length(),
                    modifiedAt = file.lastModified()
                ))
            }
        } else {
            LogStorageManager.logMessage("Импорт: Папка Загрузки не найдена или недоступна")
        }
        
        LogStorageManager.logMessage("Импорт: Всего найдено CSV файлов: ${files.size}")
        return files.sortedByDescending { it.modifiedAt }
    }
    
    fun importCsvFile(context: Context, sourcePath: String, fileName: String): Boolean {
        LogStorageManager.logMessage("Импорт: Импорт файла $fileName")
        LogStorageManager.logMessage("Импорт: Исходный путь: $sourcePath")
        return try {
            val destDir = File(context.getExternalFilesDir(null), "csv")
            if (!destDir.exists()) {
                LogStorageManager.logMessage("Импорт: Создание папки: ${destDir.absolutePath}")
                destDir.mkdirs()
            }
            
            val destPath = File(destDir, fileName).absolutePath
            LogStorageManager.logMessage("Импорт: Копирование в: $destPath")
            File(sourcePath).copyTo(File(destPath), overwrite = true)
            LogStorageManager.logMessage("Импорт: Файл успешно скопирован")
            true
        } catch (e: Exception) {
            LogStorageManager.logMessage("Импорт: ОШИБКА - ${e.message}")
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