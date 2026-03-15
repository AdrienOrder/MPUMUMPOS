package com.mobileapp

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogStorageManager {
    private val logText = StringBuilder()
    private val logLock = Any()
    private var onUpdate: (() -> Unit)? = null

    fun setUpdateListener(listener: () -> Unit) {
        onUpdate = listener
    }

    fun logMessage(msg: String) {
        val dateTime = SimpleDateFormat("HH:mm:ss dd.MM.yyyy", Locale.getDefault()).format(Date())
        val logEntry = "[$dateTime] $msg\n"
        synchronized(logLock) {
            logText.append(logEntry)
        }
        // Вызов UI обновления должен быть в UI потоке, но мы доверяем вызывающей стороне
        onUpdate?.invoke()
    }

    fun getLogs(): String {
        synchronized(logLock) {
            return logText.toString()
        }
    }

    fun clearLogs() {
        synchronized(logLock) {
            logText.clear()
        }
        onUpdate?.invoke()
    }

    fun appendLogs(newLogs: String) {
        synchronized(logLock) {
            logText.append(newLogs)
        }
        onUpdate?.invoke()
    }
}
