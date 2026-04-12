package com.mobileapp.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.mobileapp.LogStorageManager
import java.io.File

class StorageDatabaseManager(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "storage.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_DEVICES = "devices"
        private const val TABLE_CSV_FILES = "csv_files"

        private const val COL_ID = "id"
        private const val COL_NAME = "name"
        private const val COL_MAC = "mac_address"
        private const val COL_FIRST_CONNECTED = "first_connected"
        private const val COL_LAST_ACTIVE = "last_active"

        private const val COL_FILE_NAME = "file_name"
        private const val COL_FILE_PATH = "file_path"
        private const val COL_DEVICE_ID = "device_id"
        private const val COL_DOWNLOADED_AT = "downloaded_at"
        private const val COL_FILE_SIZE = "file_size"
    }

    override fun onCreate(db: SQLiteDatabase) {
        LogStorageManager.logMessage("БД: Создание таблиц БД")
        db.execSQL("""
            CREATE TABLE $TABLE_DEVICES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME TEXT NOT NULL,
                $COL_MAC TEXT UNIQUE NOT NULL,
                $COL_FIRST_CONNECTED INTEGER NOT NULL,
                $COL_LAST_ACTIVE INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_CSV_FILES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FILE_NAME TEXT NOT NULL,
                $COL_FILE_PATH TEXT NOT NULL,
                $COL_DEVICE_ID INTEGER NOT NULL,
                $COL_DOWNLOADED_AT INTEGER NOT NULL,
                $COL_FILE_SIZE INTEGER NOT NULL,
                FOREIGN KEY ($COL_DEVICE_ID) REFERENCES $TABLE_DEVICES($COL_ID) ON DELETE CASCADE
            )
        """.trimIndent())
        LogStorageManager.logMessage("БД: Таблицы созданы успешно")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        LogStorageManager.logMessage("БД: Обновление БД с версии $oldVersion на $newVersion")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CSV_FILES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DEVICES")
        onCreate(db)
    }

    fun getOrCreateDevice(name: String, macAddress: String): Device {
        LogStorageManager.logMessage("БД: Поиск устройства MAC: $macAddress")
        val db = readableDatabase
        val cursor = db.query(TABLE_DEVICES, null, "$COL_MAC = ?", arrayOf(macAddress), null, null, null)
        
        return if (cursor.moveToFirst()) {
            val device = cursorToDevice(cursor)
            LogStorageManager.logMessage("БД: Устройство найдено ID: ${device.id}, имя: ${device.name}")
            cursor.close()
            updateDeviceLastActive(device.id)
            device.copy(lastActive = System.currentTimeMillis())
        } else {
            cursor.close()
            LogStorageManager.logMessage("БД: Устройство не найдено, создание нового")
            val values = ContentValues().apply {
                put(COL_NAME, name)
                put(COL_MAC, macAddress)
                put(COL_FIRST_CONNECTED, System.currentTimeMillis())
                put(COL_LAST_ACTIVE, System.currentTimeMillis())
            }
            val id = writableDatabase.insert(TABLE_DEVICES, null, values)
            LogStorageManager.logMessage("БД: Устройство создано ID: $id, имя: $name")
            Device(id, name, macAddress)
        }
    }

    fun getAllDevices(): List<Device> {
        LogStorageManager.logMessage("БД: Загрузка всех устройств")
        val devices = mutableListOf<Device>()
        val db = readableDatabase
        val cursor = db.query(TABLE_DEVICES, null, null, null, null, null, "$COL_LAST_ACTIVE DESC")
        
        while (cursor.moveToNext()) {
            val device = cursorToDevice(cursor)
            val fileCount = getFileCountForDevice(device.id)
            devices.add(device.copy(fileCount = fileCount))
        }
        cursor.close()
        LogStorageManager.logMessage("БД: Загружено устройств: ${devices.size}")
        return devices
    }

    fun deleteDevice(deviceId: Long) {
        LogStorageManager.logMessage("БД: Удаление устройства ID: $deviceId")
        val files = getCsvFilesForDevice(deviceId)
        for (file in files) {
            LogStorageManager.logMessage("БД: Удаление файла: ${file.filePath}")
            File(file.filePath).delete()
        }
        writableDatabase.delete(TABLE_CSV_FILES, "$COL_DEVICE_ID = ?", arrayOf(deviceId.toString()))
        writableDatabase.delete(TABLE_DEVICES, "$COL_ID = ?", arrayOf(deviceId.toString()))
        LogStorageManager.logMessage("БД: Устройство и ${files.size} файлов удалены")
    }

    fun addCsvFile(fileName: String, deviceId: Long, filePath: String): CsvFile? {
        LogStorageManager.logMessage("БД: Добавление CSV файла: $fileName")
        try {
            val file = File(filePath)
            if (!file.exists()) {
                LogStorageManager.logMessage("БД: ОШИБКА - файл не существует: $filePath")
                return null
            }
            
            LogStorageManager.logMessage("БД: Файл существует, размер: ${file.length()} байт")
            
            val values = ContentValues().apply {
                put(COL_FILE_NAME, fileName)
                put(COL_FILE_PATH, filePath)
                put(COL_DEVICE_ID, deviceId)
                put(COL_DOWNLOADED_AT, System.currentTimeMillis())
                put(COL_FILE_SIZE, file.length())
            }
            val id = writableDatabase.insert(TABLE_CSV_FILES, null, values)
            if (id == -1L) {
                LogStorageManager.logMessage("БД: ОШИБКА - не удалось вставить запись в БД")
                return null
            }
            LogStorageManager.logMessage("БД: Файл добавлен в БД, ID: $id")
            return CsvFile(id, fileName, filePath, deviceId, System.currentTimeMillis(), file.length())
        } catch (e: Exception) {
            LogStorageManager.logMessage("БД: ОШИБКА - ${e.message}")
            return null
        }
    }

    fun getCsvFilesForDevice(deviceId: Long): List<CsvFile> {
        LogStorageManager.logMessage("БД: Загрузка файлов для устройства ID: $deviceId")
        val files = mutableListOf<CsvFile>()
        val db = readableDatabase
        val cursor = db.query(TABLE_CSV_FILES, null, "$COL_DEVICE_ID = ?", arrayOf(deviceId.toString()), null, null, "$COL_DOWNLOADED_AT DESC")
        
        while (cursor.moveToNext()) {
            files.add(cursorToCsvFile(cursor))
        }
        cursor.close()
        LogStorageManager.logMessage("БД: Загружено файлов: ${files.size}")
        return files
    }

    fun deleteCsvFile(fileId: Long) {
        LogStorageManager.logMessage("БД: Удаление файла ID: $fileId")
        val db = readableDatabase
        val cursor = db.query(TABLE_CSV_FILES, arrayOf(COL_FILE_PATH), "$COL_ID = ?", arrayOf(fileId.toString()), null, null, null)
        if (cursor.moveToFirst()) {
            val path = cursor.getString(0)
            LogStorageManager.logMessage("БД: Удаление файла с диска: $path")
            File(path).delete()
        } else {
            LogStorageManager.logMessage("БД: Файл не найден в БД")
        }
        cursor.close()
        writableDatabase.delete(TABLE_CSV_FILES, "$COL_ID = ?", arrayOf(fileId.toString()))
        LogStorageManager.logMessage("БД: Запись удалена из БД")
    }

    private fun getFileCountForDevice(deviceId: Long): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_CSV_FILES WHERE $COL_DEVICE_ID = ?", arrayOf(deviceId.toString()))
        var count = 0
        if (cursor.moveToFirst()) count = cursor.getInt(0)
        cursor.close()
        return count
    }

    private fun updateDeviceLastActive(deviceId: Long) {
        val values = ContentValues().apply {
            put(COL_LAST_ACTIVE, System.currentTimeMillis())
        }
        writableDatabase.update(TABLE_DEVICES, values, "$COL_ID = ?", arrayOf(deviceId.toString()))
    }

    private fun cursorToDevice(cursor: Cursor): Device {
        return Device(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
            macAddress = cursor.getString(cursor.getColumnIndexOrThrow(COL_MAC)),
            firstConnected = cursor.getLong(cursor.getColumnIndexOrThrow(COL_FIRST_CONNECTED)),
            lastActive = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LAST_ACTIVE))
        )
    }

    private fun cursorToCsvFile(cursor: Cursor): CsvFile {
        return CsvFile(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            fileName = cursor.getString(cursor.getColumnIndexOrThrow(COL_FILE_NAME)),
            filePath = cursor.getString(cursor.getColumnIndexOrThrow(COL_FILE_PATH)),
            deviceId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DEVICE_ID)),
            downloadedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DOWNLOADED_AT)),
            fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(COL_FILE_SIZE))
        )
    }

    object ContextHolder {
        lateinit var context: Context
    }
}