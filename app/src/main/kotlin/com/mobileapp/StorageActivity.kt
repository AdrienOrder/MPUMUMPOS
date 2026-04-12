package com.mobileapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import com.mobileapp.data.Device
import com.mobileapp.data.StorageDatabaseManager
import java.io.File
import java.io.FileOutputStream

class StorageActivity : AppCompatActivity() {

    companion object {
        var instance: StorageActivity? = null
    }

    private lateinit var dbManager: StorageDatabaseManager
    private lateinit var adapter: DeviceAdapter

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importFile(uri)
        }
    }

    private fun importFile(uri: Uri) {
        try {
            LogStorageManager.logMessage("Хранилище: Импорт CSV - начало")
            LogStorageManager.logMessage("Хранилище: URI файла: $uri")
            
            // Get permanent read access
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)
            LogStorageManager.logMessage("Хранилище: Получены права на чтение файла")
            
            // Read file content
            val inputStream = contentResolver.openInputStream(uri) ?: throw Exception("Cannot open file")
            
            // Try to get display name from content resolver
            var fileName: String? = null
            try {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) { }
            
            // Fallback
            if (fileName.isNullOrBlank()) {
                fileName = "import_${System.currentTimeMillis()}.csv"
            }
            
            LogStorageManager.logMessage("Хранилище: Имя файла: $fileName")
            
            // Ensure CSV extension
            if (!fileName!!.lowercase().endsWith(".csv")) {
                fileName = "$fileName.csv"
            }
            
            // Check and force CSV extension
            if (!fileName.lowercase().endsWith(".csv")) {
                fileName += ".csv"
            }
            
            // Validate it's a CSV by checking content
            val firstLine = inputStream.bufferedReader().use { it.readLine() }
            inputStream.close()
            
            LogStorageManager.logMessage("Хранилище: Первые данные: $firstLine")
            
            if (firstLine.isNullOrBlank() || !firstLine.contains(",")) {
                LogStorageManager.logMessage("Хранилище: ОШИБКА - файл не является CSV")
                Toast.makeText(this, "Это не CSV файл", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Re-open stream for actual copy
            val newInputStream = contentResolver.openInputStream(uri) ?: throw Exception("Cannot re-open file")
            
            // Save to app storage
            val destDir = File(getExternalFilesDir(null), "csv")
            if (!destDir.exists()) destDir.mkdirs()
            
            LogStorageManager.logMessage("Хранилище: Папка хранения: ${destDir.absolutePath}")
            
            val destFile = File(destDir, fileName)
            FileOutputStream(destFile).use { output ->
                newInputStream.copyTo(output)
            }
            newInputStream.close()
            
            LogStorageManager.logMessage("Хранилище: Файл скопирован: ${destFile.absolutePath}")
            LogStorageManager.logMessage("Хранилище: Размер файла: ${destFile.length()} байт")
            
            // Add to database with deviceId = 0 (imported files)
            // Using destFile.absolutePath as the file is already saved
            val result = dbManager.addCsvFile(fileName, 0, destFile.absolutePath)
            if (result == null) {
                LogStorageManager.logMessage("Хранилище: ОШИБКА - не удалось сохранить в БД")
                Toast.makeText(this, "Ошибка сохранения в БД", Toast.LENGTH_SHORT).show()
                return
            }
            
            LogStorageManager.logMessage("Хранилище: Файл добавлен в БД, ID: $result")
            Toast.makeText(this, R.string.storage_imported, Toast.LENGTH_SHORT).show()
            loadDevices()
            
            // Navigate to file list
            startActivity(Intent(this, FileListActivity::class.java).apply {
                putExtra("device_id", 0L)
                putExtra("device_name", "Импортированные")
            })
            
        } catch (e: Exception) {
            LogStorageManager.logMessage("Хранилище: ОШИБКА импорта - ${e.message}")
            Toast.makeText(this, "Ошибка импорта: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDownloadsPicker() {
        importLauncher.launch(arrayOf("text/csv", "application/octet-stream", "*/*"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        setContentView(R.layout.activity_storage)

        LogStorageManager.logMessage("=== ХРАНИЛИЩЕ: Открыто ===")

        StorageDatabaseManager.ContextHolder.context = applicationContext
        dbManager = StorageDatabaseManager(this)

        setupUI()
        loadDevices()
    }

    private fun setupUI() {
        findViewById<View>(R.id.btnImport).setOnClickListener {
            openDownloadsPicker()
        }

        adapter = DeviceAdapter(
            onDeviceClick = { device ->
                startActivity(Intent(this, FileListActivity::class.java).apply {
                    putExtra("device_id", device.id)
                    putExtra("device_name", device.name)
                })
            },
            onDeviceDelete = { device ->
                showDeleteDialog(device)
            }
        )

        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvDevices).apply {
            layoutManager = LinearLayoutManager(this@StorageActivity)
            adapter = this@StorageActivity.adapter
        }

        findViewById<View>(R.id.btnLogs).setOnClickListener {
            startActivity(Intent(this, LogDisplayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }

        findViewById<View>(R.id.btnHome).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }

        findViewById<View>(R.id.btnStorage).setOnClickListener {
            // Already here
        }
    }

    private fun loadDevices() {
        // Always show "Imported" entry for deviceId = 0
        val devices = mutableListOf<Device>()
        
        // Add "Imported" first (show even if empty to indicate functionality)
        val importedCount = dbManager.getCsvFilesForDevice(0).size
        devices.add(Device(id = 0, name = "Импортированные", macAddress = "(из папки Загрузки)", fileCount = importedCount))
        
        // Add real devices
        val allDevices = dbManager.getAllDevices()
        devices.addAll(allDevices)
        
        adapter.submitList(devices)
        findViewById<View>(R.id.tvEmpty).visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
        
        LogStorageManager.logMessage("Хранилище: Загружено устройств: ${devices.size}")
        LogStorageManager.logMessage("Хранилище: Импортированных файлов: $importedCount, устройств: ${allDevices.size}")
    }

    private fun showDeleteDialog(device: Device) {
        LogStorageManager.logMessage("Хранилище: Запрошено удаление - ${device.name}")
        
        if (device.id == 0L) {
            AlertDialog.Builder(this)
                .setTitle("Удалить файлы")
                .setMessage("Удалить все импортированные файлы?")
                .setPositiveButton("Удалить") { _, _ ->
                    val files = dbManager.getCsvFilesForDevice(0)
                    LogStorageManager.logMessage("Хранилище: Удаление ${files.size} импортированных файлов")
                    for (file in files) {
                        dbManager.deleteCsvFile(file.id)
                        LogStorageManager.logMessage("Хранилище: Удалён файл ID: ${file.id} - ${file.fileName}")
                    }
                    loadDevices()
                }
                .setNegativeButton("Отмена", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Удалить устройство")
                .setMessage("Удалить ${device.name} и все его файлы?")
                .setPositiveButton("Удалить") { _, _ ->
                    val files = dbManager.getCsvFilesForDevice(device.id)
                    LogStorageManager.logMessage("Хранилище: Удаление устройства ${device.name} и ${files.size} файлов")
                    dbManager.deleteDevice(device.id)
                    LogStorageManager.logMessage("Хранилище: Устройство удалено ID: ${device.id}")
                    loadDevices()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadDevices()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}