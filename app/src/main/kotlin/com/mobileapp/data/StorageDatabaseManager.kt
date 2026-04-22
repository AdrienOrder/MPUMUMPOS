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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CSV_FILES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DEVICES")
        onCreate(db)
    }

    fun getOrCreateDevice(name: String, macAddress: String): Device {
        val db = readableDatabase
        val cursor = db.query(TABLE_DEVICES, null, "$COL_MAC = ?", arrayOf(macAddress), null, null, null)
        
        return if (cursor.moveToFirst()) {
            val device = cursorToDevice(cursor)
            cursor.close()
            updateDeviceLastActive(device.id)
            device.copy(lastActive = System.currentTimeMillis())
        } else {
            cursor.close()
            val values = ContentValues().apply {
                put(COL_NAME, name)
                put(COL_MAC, macAddress)
                put(COL_FIRST_CONNECTED, System.currentTimeMillis())
                put(COL_LAST_ACTIVE, System.currentTimeMillis())
            }
            val id = writableDatabase.insert(TABLE_DEVICES, null, values)
            Device(id, name, macAddress)
        }
    }

    fun getAllDevices(): List<Device> {
        val devices = mutableListOf<Device>()
        val db = readableDatabase
        val cursor = db.query(TABLE_DEVICES, null, null, null, null, null, "$COL_LAST_ACTIVE DESC")
        
        while (cursor.moveToNext()) {
            val device = cursorToDevice(cursor)
            val fileCount = getFileCountForDevice(device.id)
            devices.add(device.copy(fileCount = fileCount))
        }
        cursor.close()
        return devices
    }

    fun deleteDevice(deviceId: Long) {
        val files = getCsvFilesForDevice(deviceId)
        for (file in files) {
            File(file.filePath).delete()
        }
        writableDatabase.delete(TABLE_CSV_FILES, "$COL_DEVICE_ID = ?", arrayOf(deviceId.toString()))
        writableDatabase.delete(TABLE_DEVICES, "$COL_ID = ?", arrayOf(deviceId.toString()))
    }

    fun addCsvFile(fileName: String, deviceId: Long, filePath: String): CsvFile? {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return null
            }
            
            val values = ContentValues().apply {
                put(COL_FILE_NAME, fileName)
                put(COL_FILE_PATH, filePath)
                put(COL_DEVICE_ID, deviceId)
                put(COL_DOWNLOADED_AT, System.currentTimeMillis())
                put(COL_FILE_SIZE, file.length())
            }
            val id = writableDatabase.insert(TABLE_CSV_FILES, null, values)
            if (id == -1L) {
                return null
            }
            return CsvFile(id, fileName, filePath, deviceId, System.currentTimeMillis(), file.length())
        } catch (e: Exception) {
            return null
        }
    }

    fun getCsvFilesForDevice(deviceId: Long): List<CsvFile> {
        val files = mutableListOf<CsvFile>()
        val db = readableDatabase
        val cursor = db.query(TABLE_CSV_FILES, null, "$COL_DEVICE_ID = ?", arrayOf(deviceId.toString()), null, null, "$COL_DOWNLOADED_AT DESC")
        
        while (cursor.moveToNext()) {
            files.add(cursorToCsvFile(cursor))
        }
        cursor.close()
        return files
    }

    fun deleteCsvFile(fileId: Long) {
        val db = readableDatabase
        val cursor = db.query(TABLE_CSV_FILES, arrayOf(COL_FILE_PATH), "$COL_ID = ?", arrayOf(fileId.toString()), null, null, null)
        if (cursor.moveToFirst()) {
            val path = cursor.getString(0)
            File(path).delete()
        }
        cursor.close()
        writableDatabase.delete(TABLE_CSV_FILES, "$COL_ID = ?", arrayOf(fileId.toString()))
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