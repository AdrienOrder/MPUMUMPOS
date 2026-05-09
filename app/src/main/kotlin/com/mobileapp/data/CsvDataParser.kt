package com.mobileapp.data

import com.mobileapp.LogStorageManager
import java.io.File
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
        SimpleDateFormat("dd.MM.yyyy", Locale.US),
        SimpleDateFormat("yyyy/M/d", Locale.US)
    )

    private val timeFormats = listOf(
        SimpleDateFormat("H:mm", Locale.US),
        SimpleDateFormat("H:mm:ss", Locale.US),
        SimpleDateFormat("hh:mm:ss a", Locale.US),
        SimpleDateFormat("hh:mm a", Locale.US)
    )

    fun parseCsvFile(filePath: String): CsvFileInfo? {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                LogStorageManager.logMessage("Ошибка: файл не найден")
                return null
            }

            val lines = file.readLines()

            if (lines.size < 1) {
                LogStorageManager.logMessage("Ошибка: файл пустой")
                return null
            }

            if (lines[0].contains(";")) {
                return parseDeviceDataFormat(file.name, lines)
            }

            if (lines.size < 2) {
                LogStorageManager.logMessage("Ошибка: файл пустой")
                return null
            }

            val headers = parseCsvLine(lines[0])

            val dataColumns = headers.filter { col ->
                !col.equals("Date", ignoreCase = true) &&
                !col.equals("Time", ignoreCase = true) &&
                !col.equals("Hour", ignoreCase = true) &&
                !col.equals("Data", ignoreCase = true)
            }

            val dataPoints = mutableListOf<CsvDataPoint>()

            for (i in 1 until lines.size) {
                if (lines[i].isBlank()) continue

                val values = parseCsvLine(lines[i])
                val timestamp = parseTimestamp(headers, values)

                if (timestamp == null) continue

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
                }
            }

            if (dataPoints.isNotEmpty()) {
                val minTs = dataPoints.minOf { it.timestamp }
                val maxTs = dataPoints.maxOf { it.timestamp }
                LogStorageManager.logMessage("CSV файл: ${dataPoints.size} точек данных, ${formatDate(minTs)} - ${formatDate(maxTs)}")
            } else {
                LogStorageManager.logMessage("CSV файл: нет точек данных, имен параметров: ${dataColumns.size}")
            }

            CsvFileInfo(
                fileName = file.name,
                headers = headers,
                dataColumns = dataColumns,
                dataPoints = dataPoints,
                lineCount = lines.size
            )
        } catch (e: Exception) {
            LogStorageManager.logMessage("Ошибка парсинга: ${e.message}")
            null
        }
    }

    private fun parseDeviceDataFormat(fileName: String, lines: List<String>): CsvFileInfo {
        val paramNames = mutableSetOf<String>()
        val dataPoints = mutableListOf<CsvDataPoint>()
        val deviceDateFormat = SimpleDateFormat("yyyy/M/d", Locale.US)

        for (line in lines) {
            if (line.isBlank()) continue

            val commaIdx = line.indexOf(',')
            if (commaIdx < 0) continue

            val datePart = line.substring(0, commaIdx).trim()
            val rest = line.substring(commaIdx + 1)

            val semiIdx = rest.indexOf(';')
            val timePart = if (semiIdx >= 0) rest.substring(0, semiIdx).trim() else rest.trim()
            val dataPart = if (semiIdx >= 0) rest.substring(semiIdx + 1) else ""

            val timeMinutes = timePart.replace("/0", ":00")
            val timeStr = timeMinutes.replace("/", ":")

            val timestamp = parseDeviceTimestamp(deviceDateFormat, datePart, timeStr)
            if (timestamp == null) continue

            val numericValues = mutableMapOf<String, Double>()
            if (dataPart.isNotBlank()) {
                val pairs = dataPart.split(";")
                for (pair in pairs) {
                    val colonIdx = pair.indexOf(':')
                    if (colonIdx < 0) continue

                    val key = pair.substring(0, colonIdx).trim()
                    val rawValue = pair.substring(colonIdx + 1).trim()

                    val paramName = extractParamName(key)
                    val value = rawValue.replace(",", ".").toDoubleOrNull()
                    if (value != null) {
                        numericValues[paramName] = value
                        paramNames.add(paramName)
                    }
                }
            }

            if (numericValues.isNotEmpty()) {
                dataPoints.add(CsvDataPoint(timestamp, numericValues))
            }
        }

        if (dataPoints.isNotEmpty()) {
            val minTs = dataPoints.minOf { it.timestamp }
            val maxTs = dataPoints.maxOf { it.timestamp }
            LogStorageManager.logMessage("CSV файл: ${dataPoints.size} точек данных, ${formatDate(minTs)} - ${formatDate(maxTs)}")
        }

        return CsvFileInfo(
            fileName = fileName,
            headers = paramNames.toList(),
            dataColumns = paramNames.toList(),
            dataPoints = dataPoints,
            lineCount = lines.size
        )
    }

    private fun extractParamName(key: String): String {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return key
        val letterIdx = trimmed.indexOfFirst { it.isLetter() }
        if (letterIdx < 0) return key
        val sensorId = trimmed.substring(0, letterIdx)
        val paramName = trimmed.substring(letterIdx)
        return "$sensorId: $paramName"
    }

    private fun parseDeviceTimestamp(dateFormat: SimpleDateFormat, dateStr: String, timeStr: String): Long? {
        return try {
            val date = dateFormat.parse(dateStr) ?: return null
            val cal = java.util.Calendar.getInstance()
            cal.time = date

            if (timeStr.isNotBlank()) {
                val timeParts = timeStr.split(":")
                if (timeParts.size >= 2) {
                    cal.set(java.util.Calendar.HOUR_OF_DAY, timeParts[0].toIntOrNull() ?: 0)
                    cal.set(java.util.Calendar.MINUTE, timeParts[1].toIntOrNull() ?: 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                }
            }

            cal.timeInMillis
        } catch (e: Exception) {
            null
        }
    }

    private fun formatDate(ts: Long): String {
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ts))
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
