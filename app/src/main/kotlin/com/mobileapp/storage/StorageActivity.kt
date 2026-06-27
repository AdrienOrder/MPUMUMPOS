package com.mobileapp.storage

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.mobileapp.bluetooth.DeviceAdapter
import com.mobileapp.bluetooth.data.Device
import com.mobileapp.log.LogDisplayActivity
import com.mobileapp.log.LogStorageManager
import com.mobileapp.main.MainActivity
import com.mobileapp.storage.data.StorageDatabaseManager
import com.mobileapp.R
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
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)

            val inputStream = contentResolver.openInputStream(uri) ?: throw Exception("Cannot open file")

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

            val finalFileName = fileName ?: "import_${System.currentTimeMillis()}.csv"
            val isCsv = finalFileName.lowercase().endsWith(".csv")
            val isTxt = finalFileName.lowercase().endsWith(".txt")
            if (!isCsv && !isTxt) {
                LogStorageManager.logMessage("Ошибка импорта: файл должен быть .csv или .txt")
                Toast.makeText(this, "Файл должен быть .csv или .txt", Toast.LENGTH_SHORT).show()
                inputStream.close()
                return
            }

            val firstLine = inputStream.bufferedReader().use { it.readLine() }
            inputStream.close()

            if (firstLine.isNullOrBlank() || (!firstLine.contains(",") && !firstLine.contains(";"))) {
                LogStorageManager.logMessage("Ошибка импорта: выбранный файл не является CSV файлом")
                Toast.makeText(this, "Это не CSV файл", Toast.LENGTH_SHORT).show()
                return
            }

            val newInputStream = contentResolver.openInputStream(uri) ?: throw Exception("Cannot re-open file")

            val destDir = File(getExternalFilesDir(null), "csv")
            if (!destDir.exists()) destDir.mkdirs()

            val destFile = File(destDir, finalFileName)
            FileOutputStream(destFile).use { output ->
                newInputStream.copyTo(output)
            }
            newInputStream.close()

            val result = dbManager.addCsvFile(finalFileName, 0, destFile.absolutePath)
            if (result == null) {
                LogStorageManager.logMessage("Ошибка сохранения в БД")
                Toast.makeText(this, "Ошибка сохранения в БД", Toast.LENGTH_SHORT).show()
                return
            }

            LogStorageManager.logMessage("Импорт: $fileName")
            Toast.makeText(this, R.string.storage_imported, Toast.LENGTH_SHORT).show()
            loadDevices()

            startActivity(Intent(this, FileListActivity::class.java).apply {
                putExtra("device_id", 0L)
                putExtra("device_name", "Импортированные")
            })

        } catch (e: Exception) {
            LogStorageManager.logMessage("Ошибка импорта: ${e.message}")
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
        }
    }

    private fun loadDevices() {
        val devices = mutableListOf<Device>()

        val importedCount = dbManager.getCsvFilesForDevice(0).size
        devices.add(Device(id = 0, name = "Импортированные", macAddress = "(из папки Загрузки)", fileCount = importedCount))

        val allDevices = dbManager.getAllDevices()
        devices.addAll(allDevices)

        adapter.submitList(devices)
        findViewById<View>(R.id.tvEmpty).visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE

        val deviceDownloadedCount = allDevices.sumOf { device -> dbManager.getCsvFilesForDevice(device.id).size }
        LogStorageManager.logMessage("Хранилище: импортировано из Загрузок: $importedCount файлов, скачано с устройств: $deviceDownloadedCount файлов, всего устройств: ${allDevices.size}")
    }

    private fun showDeleteDialog(device: Device) {
        if (device.id == 0L) {
            val titleView = TextView(this).apply {
                text = "Удалить файлы"
                setTextColor(ContextCompat.getColor(this@StorageActivity, R.color.blue_500))
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 40, 0, 20)
            }
            AlertDialog.Builder(this)
                .setCustomTitle(titleView)
                .setMessage("Удалить все импортированные файлы?")
                .setPositiveButton("Удалить") { _, _ ->
                    val files = dbManager.getCsvFilesForDevice(0)
                    for (file in files) {
                        dbManager.deleteCsvFile(file.id)
                    }
                    loadDevices()
                }
                .setNegativeButton("Отмена", null)
                .show()
        } else {
            val titleView = TextView(this).apply {
                text = "Удалить устройство"
                setTextColor(ContextCompat.getColor(this@StorageActivity, R.color.blue_500))
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 40, 0, 20)
            }
            AlertDialog.Builder(this)
                .setCustomTitle(titleView)
                .setMessage("Удалить ${device.name} и все его файлы?")
                .setPositiveButton("Удалить") { _, _ ->
                    dbManager.deleteDevice(device.id)
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
