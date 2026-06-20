package com.mobileapp.csv.data

data class CsvFile(
    val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val deviceId: Long,
    val downloadedAt: Long = System.currentTimeMillis(),
    val fileSize: Long = 0
)
