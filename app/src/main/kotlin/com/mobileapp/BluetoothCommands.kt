package com.mobileapp

object BluetoothCommands {
    // Команды для устройства
    const val CMD_SET_INTERVAL = "SET interval "
    const val CMD_SET_START = "SET start "
    const val CMD_SET_TIME = "SET time "
    const val CMD_GET_DATA = "GET data"
    const val CMD_GET_INTERVAL = "GET interval"
    const val CMD_GET_START = "GET start"
    const val CMD_GET_TIME = "GET time"

    // Конвертация минут в формат ДД:ЧЧ:ММ
    fun minutesToDDHHMM(totalMinutes: Int): String {
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60
        return String.format("%02d:%02d:%02d", days, hours, minutes)
    }

    // Конвертация из формата ДД:ЧЧ:ММ в минуты
    fun ddHHMMToMinutes(ddHHMM: String): Int? {
        val parts = ddHHMM.split(":")
        if (parts.size == 3) {
            val days = parts[0].toIntOrNull() ?: 0
            val hours = parts[1].toIntOrNull() ?: 0
            val minutes = parts[2].toIntOrNull() ?: 0
            return days * 24 * 60 + hours * 60 + minutes
        }
        return ddHHMM.toIntOrNull()
    }
}