package com.mobileapp.bluetooth.data

data class Device(
    val id: Long = 0,
    val name: String,
    val macAddress: String,
    val firstConnected: Long = System.currentTimeMillis(),
    val lastActive: Long = System.currentTimeMillis(),
    val fileCount: Int = 0
)
