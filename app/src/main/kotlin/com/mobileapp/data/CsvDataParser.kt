package com.mobileapp.data

import com.mobileapp.LogStorageManager
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

data class CsvDataPoint(
    val timestamp: Long,
    val values: Map<String, Double>
)

data class CsvFileInfo(
    val fileName: String,
    val headers: List<String>,
    val dataColumns: List<String>,
    val dataPoints: List<CsvDataPoint>,
    val lineCount: Int
)

object CsvDataParser {
    
    private val dateFormats = listOf(
        SimpleDateFormat("M/d/yyyy", Locale.US),
        SimpleDateFormat("d/M/yyyy", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
        SimpleDateFormat("dd.MM.yyyy", Locale.US)
    )
    
    private val timeFormats = listOf(
        SimpleDateFormat("H:mm", Locale.US),
        SimpleDateFormat("H:mm:ss", Locale.US),
        SimpleDateFormat("hh:mm:ss a", Locale.US),
        SimpleDateFormat("hh:mm a", Locale.US)
    )
    
    fun parseCsvFile(filePath: String): CsvFileInfo? {
        LogStorageManager.logMessage("CSV: Парсинг файла: $filePath")
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                LogStorageManager.logMessage("CSV: ОШИБКА - файл не существует")
                return null
            }
            
            LogStorageManager.logMessage("CSV: Файл найден, размер: ${file.length()} байт")
            
            val lines = file.readLines()
            LogStorageManager.logMessage("CSV: Прочитано строк: ${lines.size}")
            
            if (lines.size < 2) {
                LogStorageManager.logMessage("CSV: ОШИБКА - файл пустой или менее 2 строк")
                return null
            }
            
            val headers = parseCsvLine(lines[0])
            LogStorageManager.logMessage("CSV: Заголовки: $headers")
            
            val dataColumns = headers.filter { col ->
                !col.equals("Date", ignoreCase = true) &&
                !col.equals("Time", ignoreCase = true) &&
                !col.equals("Hour", ignoreCase = true) &&
                !col.equals("Data", ignoreCase = true)
            }
            LogStorageManager.logMessage("CSV: Столбцы данных: $dataColumns")
            
            val dataPoints = mutableListOf<CsvDataPoint>()
            var skippedEmpty = 0
            var skippedNoTimestamp = 0
            var skippedNoValues = 0
            
            for (i in 1 until lines.size) {
                if (lines[i].isBlank()) {
                    skippedEmpty++
                    continue
                }
                
                val values = parseCsvLine(lines[i])
                val timestamp = parseTimestamp(headers, values)
                
                if (timestamp == null) {
                    skippedNoTimestamp++
                    continue
                }
                
                val numericValues = mutableMapOf<String, Double>()
                for (col in dataColumns) {
                    val index = headers.indexOf(col)
                    if (index >= 0 && index < values.size) {
                        val strVal = values[index].trim()
                        if (strVal.isNotBlank() && strVal != "(NO REPLY)") {
                            strVal.toDoubleOrNull()?.let { numericValues[col] = it }
                        }
                    }
                }
                if (numericValues.isNotEmpty()) {
                    dataPoints.add(CsvDataPoint(timestamp, numericValues))
                } else {
                    skippedNoValues++
                }
            }
            
            LogStorageManager.logMessage("CSV: Обработано точек: ${dataPoints.size}")
            LogStorageManager.logMessage("CSV: Пропущено (пустые): $skippedEmpty, (нет timestamp): $skippedNoTimestamp, (нет значений): $skippedNoValues")
            
            if (dataPoints.isNotEmpty()) {
                val minTs = dataPoints.minOf { it.timestamp }
                val maxTs = dataPoints.maxOf { it.timestamp }
                LogStorageManager.logMessage("CSV: Диапазон дат: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(minTs))} - ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(maxTs))}")
            }
            
            CsvFileInfo(
                fileName = file.name,
                headers = headers,
                dataColumns = dataColumns,
                dataPoints = dataPoints,
                lineCount = lines.size
            )
        } catch (e: Exception) {
            LogStorageManager.logMessage("CSV: ОШИБКА парсинга - ${e.message}")
            null
        }
    }
    
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(current.toString().trim().removeSurrounding("\""))
                    current = StringBuilder()
                }
                else -> current.append(c)
            }
        }
        result.add(current.toString().trim().removeSurrounding("\""))
        
        return result
    }
    
    private fun parseTimestamp(headers: List<String>, values: List<String>): Long? {
        val dateIndex = headers.indexOfFirst { it.equals("Date", ignoreCase = true) || it.equals("Data", ignoreCase = true) }
        val timeIndex = headers.indexOfFirst { it.equals("Time", ignoreCase = true) || it.equals("Hour", ignoreCase = true) }
        
        if (dateIndex < 0 || dateIndex >= values.size) return null
        
        val dateStr = values[dateIndex].trim()
        val timeStr = if (timeIndex >= 0 && timeIndex < values.size) values[timeIndex].trim() else ""
        
        for (df in dateFormats) {
            for (tf in timeFormats) {
                try {
                    val date = df.parse(dateStr) ?: continue
                    val time = if (timeStr.isNotBlank()) tf.parse(timeStr) else null
                    
                    val calendar = java.util.Calendar.getInstance()
                    calendar.time = date
                    
                    if (time != null) {
                        val timeCal = java.util.Calendar.getInstance()
                        timeCal.time = time
                        calendar.set(java.util.Calendar.HOUR_OF_DAY, timeCal.get(java.util.Calendar.HOUR_OF_DAY))
                        calendar.set(java.util.Calendar.MINUTE, timeCal.get(java.util.Calendar.MINUTE))
                        calendar.set(java.util.Calendar.SECOND, timeCal.get(java.util.Calendar.SECOND))
                    }
                    
                    return calendar.timeInMillis
                } catch (e: Exception) {
                    continue
                }
            }
        }
        
        return try {
            for (df in dateFormats) {
                val date = df.parse(dateStr)
                if (date != null) return date.time
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}