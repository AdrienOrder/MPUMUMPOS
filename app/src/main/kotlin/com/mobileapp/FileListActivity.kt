package com.mobileapp

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.mobileapp.data.CsvFile
import com.mobileapp.data.StorageDatabaseManager
import java.io.File

class FileListActivity : AppCompatActivity() {

    private lateinit var dbManager: StorageDatabaseManager
    private lateinit var adapter: FileAdapter
    private var deviceId: Long = 0
    private var deviceName: String = "Импортированные"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_list)

        deviceId = intent.getLongExtra("device_id", 0)
        deviceName = intent.getStringExtra("device_name") ?: ""

        LogStorageManager.logMessage("=== ФАЙЛЫ: Открыто устройство '$deviceName' (ID: $deviceId) ===")

        StorageDatabaseManager.ContextHolder.context = applicationContext
        dbManager = StorageDatabaseManager(this)

        setupUI()
        loadFiles()
    }

    private fun setupUI() {
        findViewById<android.widget.TextView>(R.id.tvTitle).text = getString(R.string.file_list_title)
        findViewById<android.widget.TextView>(R.id.tvDeviceName).text = deviceName

        adapter = FileAdapter(
            onDownloadClick = { file ->
                downloadFile(file)
            },
            onVisualizeClick = { file ->
                startActivity(Intent(this, VisualizationActivity::class.java).apply {
                    putExtra("file_path", file.filePath)
                    putExtra("file_name", file.fileName)
                })
            },
            onDeleteClick = { file ->
                val dialogView = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 40, 60, 20)
                    
                    addView(TextView(context).apply {
                        text = "Удалить файл"
                        setTextColor(ContextCompat.getColor(context, R.color.blue_500))
                        textSize = 20f
                        gravity = android.view.Gravity.CENTER
                    })
                    addView(TextView(context).apply {
                        text = file.fileName
                        setTextColor(ContextCompat.getColor(context, R.color.white))
                        textSize = 16f
                        gravity = android.view.Gravity.CENTER
                        setPadding(0, 20, 0, 40)
                    })
                }
                
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setView(dialogView)
                    .setPositiveButton("Удалить") { _, _ ->
                        dbManager.deleteCsvFile(file.id)
                        loadFiles()
                        LogStorageManager.logMessage("Удален файл: ${file.fileName}")
                        Toast.makeText(this, R.string.file_deleted, Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        )

        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvFiles).apply {
            layoutManager = LinearLayoutManager(this@FileListActivity)
            adapter = this@FileListActivity.adapter
        }
    }

    private fun loadFiles() {
        val files = dbManager.getCsvFilesForDevice(deviceId)
        adapter.submitList(files)
        findViewById<View>(R.id.tvEmpty).visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        LogStorageManager.logMessage("Файлы: Загружено файлов: ${files.size}")
    }

    private fun downloadFile(file: CsvFile) {
        try {
            LogStorageManager.logMessage("Файлы: Скачивание файла: ${file.fileName}")
            LogStorageManager.logMessage("Файлы: Путь файла: ${file.filePath}")
            
            val sourceFile = File(file.filePath)
            LogStorageManager.logMessage("Файлы: Исходный файл существует: ${sourceFile.exists()}")
            LogStorageManager.logMessage("Файлы: Размер исходного файла: ${sourceFile.length()} байт")
            
            // Create MPUMUMPOS Downloads folder in external storage
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val mpumFolder = File(downloadsDir, "MPUMUMPOS Downloads")
            if (!mpumFolder.exists()) mpumFolder.mkdirs()
            
            LogStorageManager.logMessage("Файлы: Папка загрузок: ${mpumFolder.absolutePath}")
            
            // Handle duplicate files with version number
            var baseName = file.fileName.substringBeforeLast(".csv")
            var ext = ".csv"
            var destFile = File(mpumFolder, file.fileName)
            var version = 1
            
            while (destFile.exists()) {
                LogStorageManager.logMessage("Файлы: Файл уже существует, добавление версии $version")
                destFile = File(mpumFolder, "$baseName ($version)$ext")
                version++
            }
            
            sourceFile.copyTo(destFile)
            LogStorageManager.logMessage("Файлы: Файл скопирован: ${destFile.name}")
            LogStorageManager.logMessage("Файлы: Размер скачанного файла: ${destFile.length()} байт")
            Toast.makeText(this, "${destFile.name} скачан", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            LogStorageManager.logMessage("Файлы: ОШИБКА скачивания - ${e.message}")
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadFiles()
    }
}