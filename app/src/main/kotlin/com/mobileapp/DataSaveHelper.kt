package com.mobileapp

import android.content.Context
import com.mobileapp.data.StorageDatabaseManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataSaveHelper {
    private var infoBuffer = StringBuilder()
    private var dataBuffer = StringBuilder()
    private var isCollectingInfo = false
    private var isCollectingData = false
    private var currentDeviceMac = ""
    private var currentDeviceName = ""
    private var wasSaved = false

    fun startCollecting(deviceMac: String, deviceName: String) {
        infoBuffer = StringBuilder()
        dataBuffer = StringBuilder()
        isCollectingInfo = false
        isCollectingData = false
        wasSaved = false
        currentDeviceMac = deviceMac
        currentDeviceName = deviceName
        LogStorageManager.logMessage("Сбор данных с устройства начат")
    }

    fun processLine(line: String): Boolean {
        LogStorageManager.logMessage("DataSaveHelper.processLine: [${line.take(50)}]")
        return when {
            line.startsWith("!info:") -> {
                isCollectingInfo = true
                isCollectingData = false
                infoBuffer.append(line.removePrefix("!info:")).append("\n")
                true
            }
            line.startsWith("!data:") -> {
                isCollectingData = true
                isCollectingInfo = false
                dataBuffer.append(line.removePrefix("!data:")).append("\n")
                true
            }
            isCollectingInfo -> {
                infoBuffer.append(line).append("\n")
                true
            }
            isCollectingData -> {
                dataBuffer.append(line).append("\n")
                true
            }
            else -> false
        }
    }

    fun hasData(): Boolean {
        return infoBuffer.isNotEmpty() || dataBuffer.isNotEmpty()
    }

    fun isSaved(): Boolean = wasSaved

    fun saveFiles(context: Context): Boolean {
        if (infoBuffer.isEmpty() && dataBuffer.isEmpty()) {
            LogStorageManager.logMessage("Нет данных для сохранения")
            return false
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dbManager = StorageDatabaseManager(context)
        val device = dbManager.getOrCreateDevice(currentDeviceName, currentDeviceMac)

        val deviceDir = File(context.getExternalFilesDir(null), "devices/${device.id}")
        if (!deviceDir.exists()) deviceDir.mkdirs()

        var infoSaved = false
        var dataSaved = false

        if (infoBuffer.isNotEmpty()) {
            val infoFile = File(deviceDir, "info_$timestamp.txt")
            infoFile.writeText(infoBuffer.toString().trimEnd())
            dbManager.addCsvFile("info_$timestamp.txt", device.id, infoFile.absolutePath)
            infoSaved = true
            LogStorageManager.logMessage("Сохранен информационный файл: ${infoFile.name}")
        }

        if (dataBuffer.isNotEmpty()) {
            val dataFile = File(deviceDir, "data_$timestamp.csv")
            dataFile.writeText(dataBuffer.toString().trimEnd())
            dbManager.addCsvFile("data_$timestamp.csv", device.id, dataFile.absolutePath)
            dataSaved = true
            LogStorageManager.logMessage("Сохранен файл данных: ${dataFile.name}")
        }

        wasSaved = infoSaved || dataSaved
        reset()
        return infoSaved || dataSaved
    }

    private fun reset() {
        infoBuffer = StringBuilder()
        dataBuffer = StringBuilder()
        isCollectingInfo = false
        isCollectingData = false
        wasSaved = false
    }
}
