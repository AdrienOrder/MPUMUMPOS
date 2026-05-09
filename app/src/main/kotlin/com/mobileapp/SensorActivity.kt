package com.mobileapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SensorActivity : AppCompatActivity() {

    companion object {
        private const val MAX_SENSOR_ID = 999
        private const val MAX_STATUS2_RETRIES = 5
    }

    private val sensorList = mutableListOf<SensorItem>()
    private var currentDeviceMac: String = ""
    private var currentDeviceName: String = ""
    private var bluetoothManager: BluetoothManager? = null
    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingRefresh: Runnable? = null
    private var status2RetryCount = 0

    data class SensorItem(
        val id: String,
        var status: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor)

        currentDeviceMac = intent.getStringExtra("device_mac") ?: ""
        currentDeviceName = intent.getStringExtra("device_name") ?: "Устройство"

        bluetoothManager = BluetoothManager.getExistingInstance()
        if (bluetoothManager == null) {
            Toast.makeText(this, "Bluetooth не инициализирован", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        BluetoothManager.setAdditionalHandler { response ->
            processSensorResponse(response)
        }

        initViews()
        setupButtons()

        refreshHandler.postDelayed({
            requestSensorsList()
        }, 300)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelPendingRefresh()
        BluetoothManager.setAdditionalHandler(null)
    }

    private fun processSensorResponse(response: String) {
        val trimmed = response.trim()

        if (trimmed.contains("Список датчиков пуст", ignoreCase = true)) {
            status2RetryCount = 0
            runOnUiThread {
                sensorList.clear()
                updateSensorList()
                LogStorageManager.logMessage("Список датчиков пуст")
            }
            return
        }

        if (trimmed.contains("Датчик уже добавлен", ignoreCase = true) || trimmed.contains("already added", ignoreCase = true)) {
            runOnUiThread {
                LogStorageManager.logMessage("Датчик: $trimmed")
                Toast.makeText(this, trimmed, Toast.LENGTH_LONG).show()
                val idFound = trimmed.filter { it.isDigit() }
                if (idFound.isNotEmpty()) {
                    sensorList.removeAll { it.id == idFound }
                    updateSensorList()
                }
            }
            return
        }

        if (trimmed.startsWith("ERROR", ignoreCase = true)) {
            runOnUiThread {
                LogStorageManager.logMessage("Датчик: $trimmed")
                Toast.makeText(this, trimmed, Toast.LENGTH_SHORT).show()
                val idFound = trimmed.filter { it.isDigit() }
                if (idFound.isNotEmpty()) {
                    val existing = sensorList.find { it.id == idFound }
                    if (existing != null) {
                        existing.status = 0
                        updateSensorList()
                    }
                } else {
                    sensorList.filter { it.status == 2 }.forEach { it.status = 0 }
                    if (sensorList.any { it.status == 0 }) updateSensorList()
                }
            }
            return
        }

        if (trimmed.startsWith("OK", ignoreCase = true)) {
            runOnUiThread {
                LogStorageManager.logMessage("Датчик: $trimmed")
            }
            return
        }

        var foundAny = false
        val entries = trimmed.split(",")
        for (entry in entries) {
            val parts = entry.trim().split(":").map { it.trim() }
            if (parts.size >= 2) {
                val status = parts.last().toIntOrNull()
                if (status != null && status in 0..2) {
                    val id = parts[parts.size - 2]
                    if (id.isNotEmpty() && id.all(Char::isDigit)) {
                        foundAny = true
                        runOnUiThread { addOrUpdateSensor(id, status) }
                    }
                }
            }
        }
        if (foundAny) {
            status2RetryCount = 0
            runOnUiThread { updateSensorList() }
            return
        }

        runOnUiThread {
            LogStorageManager.logMessage("Датчик: $trimmed")
        }
    }

    private fun initViews() {
        val backBtn = findViewById<ImageButton>(R.id.btnBack)
        if (backBtn != null) {
            backBtn.setOnClickListener { finish() }
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnAddSensor)?.setOnClickListener {
            showAddSensorDialog()
        }

        findViewById<Button>(R.id.btnRefreshSensors)?.setOnClickListener {
            requestSensorsList()
        }
    }

    private fun showAddSensorDialog() {
        val inputEditText = EditText(this).apply {
            hint = "Введите ID датчика"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 32, 48, 32)
        }

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(TextView(context).apply {
                text = "Ввод ID датчика"
                setTextColor(ContextCompat.getColor(context, R.color.blue_500))
                textSize = 20f
                gravity = android.view.Gravity.CENTER
            })
            addView(inputEditText)
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("ПОДТВЕРДИТЬ") { _, _ ->
                val sensorId = inputEditText.text.toString().trim()
                if (sensorId.isNotEmpty()) {
                    addSensor(sensorId)
                }
            }
            .setNegativeButton("ОТМЕНА", null)
            .show()
    }

    private fun addSensor(sensorId: String) {
        val idNum = sensorId.toIntOrNull()
        if (idNum == null || idNum < 1 || idNum > MAX_SENSOR_ID) {
            Toast.makeText(this, "Некорректный ID датчика", Toast.LENGTH_SHORT).show()
            return
        }
        LogStorageManager.logMessage("Добавление датчика по ID: $sensorId")

        addOrUpdateSensor(sensorId, 2)
        updateSensorList()

        bluetoothManager?.sendCommand(MainActivity.CMD_ADD_SENSOR + sensorId)
        Toast.makeText(this, "Запрос на добавление датчика: $sensorId", Toast.LENGTH_SHORT).show()
    }

    private fun requestSensorsList() {
        LogStorageManager.logMessage("Запрос списка датчиков")
        status2RetryCount = 0
        cancelPendingRefresh()
        sensorList.clear()
        updateSensorList()
        bluetoothManager?.sendCommand(MainActivity.CMD_GET_SENSORS)
    }

    private fun cancelPendingRefresh() {
        pendingRefresh?.let { refreshHandler.removeCallbacks(it) }
        pendingRefresh = null
    }

    private fun scheduleDelayedRefresh(delayMs: Long) {
        status2RetryCount++
        if (status2RetryCount > MAX_STATUS2_RETRIES) {
            status2RetryCount = 0
            sensorList.filter { it.status == 2 }.forEach { it.status = 0 }
            updateSensorList()
            return
        }
        cancelPendingRefresh()
        pendingRefresh = Runnable {
            pendingRefresh = null
            bluetoothManager?.sendCommand(MainActivity.CMD_GET_SENSORS)
        }
        refreshHandler.postDelayed(pendingRefresh!!, delayMs)
    }

    private fun addOrUpdateSensor(id: String, status: Int) {
        val existing = sensorList.find { it.id == id }
        if (existing != null) {
            existing.status = status
        } else {
            sensorList.add(SensorItem(id, status))
        }
    }

    private fun deleteSensor(sensorId: String) {
        val titleView = TextView(this).apply {
            text = "Удалить датчик с ID $sensorId?"
            setTextColor(ContextCompat.getColor(this@SensorActivity, R.color.blue_500))
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 40, 0, 20)
        }

        val dialog = AlertDialog.Builder(this)
            .setCustomTitle(titleView)
            .setPositiveButton("ПОДТВЕРДИТЬ") { _, _ ->
                LogStorageManager.logMessage("Датчик удален: $sensorId")
                bluetoothManager?.sendCommand(MainActivity.CMD_DEL_SENSOR + sensorId)
                sensorList.removeAll { it.id == sensorId }
                updateSensorList()
                refreshHandler.postDelayed({
                    requestSensorsList()
                }, 500)
            }
            .setNegativeButton("ОТМЕНА", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(this@SensorActivity, R.color.blue_500))
                    getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(this@SensorActivity, R.color.blue_500))
                }
            }
            .show()
    }

    private fun updateSensorList() {
        val container = findViewById<LinearLayout>(R.id.sensorListContainer)
        container?.removeAllViews()

        val emptyView = findViewById<TextView>(R.id.tvEmpty)
        if (sensorList.isEmpty()) {
            emptyView?.visibility = View.VISIBLE
            return
        }
        emptyView?.visibility = View.GONE

        val hasStatus2 = sensorList.any { it.status == 2 }
        if (hasStatus2) {
            scheduleDelayedRefresh(3000)
        }

        for (sensor in sensorList) {
            val statusIcon = when (sensor.status) {
                1 -> "\u2713"
                0 -> "\u2717"
                else -> "\uD83D\uDD0D"
            }
            val statusColor = when (sensor.status) {
                1 -> ContextCompat.getColor(this, R.color.connected_green)
                0 -> ContextCompat.getColor(this, R.color.disconnected_red)
                else -> ContextCompat.getColor(this, R.color.purple_500)
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 8) }
                setPadding(16, 16, 16, 16)
                background = ContextCompat.getDrawable(context, R.drawable.item_sensor_background)
            }

            row.addView(TextView(this).apply {
                text = "ID: ${sensor.id}"
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.black))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            val centerContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            centerContainer.addView(TextView(this).apply {
                text = statusIcon
                textSize = 24f
                setTextColor(statusColor)
                gravity = android.view.Gravity.CENTER
            })
            row.addView(centerContainer)

            val rightContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            rightContainer.addView(Button(this).apply {
                text = "Удалить"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.white))
                setBackgroundResource(R.drawable.button_delete_red)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(40)
                )
                setOnClickListener { deleteSensor(sensor.id) }
            })
            row.addView(rightContainer)

            container?.addView(row)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
