package com.mobileapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.widget.ImageButton
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"

        // Команды для устройства
        private const val CMD_SET_INTERVAL = "SET interval "
        private const val CMD_SET_START = "SET start "
        private const val CMD_SET_TIME = "SET time "
        private const val CMD_GET_DATA = "GET data"
        private const val CMD_GET_INTERVAL = "GET interval"
        private const val CMD_GET_START = "GET start"
        private const val CMD_GET_TIME = "GET time"

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
            return ddHHMM.toIntOrNull() // null если некорректный формат
        }
    }

    private lateinit var btnConnect: Button
    private lateinit var tvDeviceStatus: TextView
    private lateinit var tvDeviceName: TextView
    private lateinit var intervalEditText: EditText
    private lateinit var startTimeEditText: EditText
    private lateinit var btnEditParams: Button
    private lateinit var deviceTimeText: TextView
    private lateinit var btnSyncTime: Button
    private lateinit var btnDownloadStorage: Button

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var dialogManager: DialogManager

    private var isEditingMode = false
    private var previousInterval: String = ""
    private var previousStartTime: String = ""
    private var shouldCheckPermissionsOnResume = false

    private val handler = Handler(Looper.getMainLooper())
    private var timeRequestRunnable: Runnable? = null

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            checkBluetoothAndOpenDeviceList()
        } else {
            LogStorageManager.logMessage("Пользователь отказал в разрешениях")
            // Show dialog directing user to settings
            dialogManager.showPermissionsRequiredDialog(
                onOpenSettings = {
                    shouldCheckPermissionsOnResume = true
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = android.net.Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }
            )
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            LogStorageManager.logMessage("Bluetooth включен")
            openDeviceList()
        } else {
            Toast.makeText(this, R.string.toast_bluetooth_disabled, Toast.LENGTH_SHORT).show()
            LogStorageManager.logMessage("Пользователь отказался включать Bluetooth")
        }
    }

    private val deviceListLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val deviceAddress = result.data?.getStringExtra("device_address")
            if (deviceAddress != null) {
                bluetoothManager.connect(deviceAddress)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate начат")

        try {
            setContentView(R.layout.activity_main)
            initViews()
            initHiddenViews()
            initManagers()
            setupUI()
            
            // Проверяем реальное состояние подключения
            val wasConnected = bluetoothManager.isConnected()
            updateUIState(wasConnected)
            
            LogStorageManager.logMessage("Приложение запущено")
            Log.d(TAG, "onCreate завершен успешно, wasConnected: $wasConnected")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в onCreate: ${e.message}", e)
            Toast.makeText(this, R.string.toast_error_start, Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews() {
        btnConnect = findViewById(R.id.btnConnect)
        tvDeviceStatus = findViewById(R.id.tvDeviceStatus)
        tvDeviceName = findViewById(R.id.tvDeviceName)
        intervalEditText = findViewById(R.id.etInterval)
        startTimeEditText = findViewById(R.id.etStartTime)
        btnEditParams = findViewById(R.id.btnEditParams)
        deviceTimeText = findViewById(R.id.tvDeviceTime)
        btnSyncTime = findViewById(R.id.btnSyncTime)
        btnDownloadStorage = findViewById(R.id.btnDownloadStorage)
    }

    // Views to hide/show
    private lateinit var tvDeviceNameLabel: TextView
    private lateinit var intervalGroup: android.view.View
    private lateinit var startTimeGroup: android.view.View
    private lateinit var deviceTimeGroup: android.view.View
    private lateinit var buttonGroup: android.view.View

    private fun initHiddenViews() {
        tvDeviceNameLabel = findViewById(R.id.tvDeviceNameLabel)
        intervalGroup = findViewById(R.id.intervalGroup)
        startTimeGroup = findViewById(R.id.startTimeGroup)
        deviceTimeGroup = findViewById(R.id.deviceTimeGroup)
        buttonGroup = findViewById(R.id.buttonGroup)
    }

    private fun initManagers() {
        dialogManager = DialogManager(this)

        // Используем синглтон для сохранения соединения при навигации
        if (BluetoothManager.isInstanceCreated()) {
            bluetoothManager = BluetoothManager.getExistingInstance()!!
        } else {
            bluetoothManager = BluetoothManager.getInstance(
                handler = handler,
                onConnected = { device ->
                    updateUIState(true)
                    LogStorageManager.logMessage("Подключено к устройству: ${device.name}")
                    requestDeviceSettings()
                },
                onDisconnected = {
                    updateUIState(false)
                    LogStorageManager.logMessage("Устройство отключено")
                    Toast.makeText(this, "Соединение с устройством потеряно", Toast.LENGTH_LONG).show()
                },
                onMessageReceived = { response ->
                    processResponse(response)
                },
                onError = { error ->
                    LogStorageManager.logMessage("Ошибка Bluetooth: $error")
                    if (!error.contains("Ошибка чтения")) {
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        LogStorageManager.setUpdateListener {
            // Update LogActivity if needed
            LogDisplayActivity.updateLogDisplay()
        }
    }

    private fun requestDeviceSettings() {
        // Запрос текущих настроек с устройства (если поддерживается)
        LogStorageManager.logMessage("Запрос текущих настроек с устройства...")
        bluetoothManager.sendCommand(CMD_GET_INTERVAL)
        handler.postDelayed({
            bluetoothManager.sendCommand(CMD_GET_START)
        }, 100)
        handler.postDelayed({
            bluetoothManager.sendCommand(CMD_GET_TIME)
        }, 200)
    }

    private fun setupUI() {
        btnConnect.setOnClickListener {
            Log.d(TAG, "Connect button clicked")
            LogStorageManager.logMessage("Нажата кнопка подключения")
            if (bluetoothManager.isConnected()) {
                bluetoothManager.disconnect()
            } else {
                checkAndRequestPermissions()
            }
        }

        btnEditParams.setOnClickListener {
            if (!bluetoothManager.isConnected()) {
                Toast.makeText(this, R.string.toast_connect_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isEditingMode) {
                isEditingMode = true
                previousInterval = intervalEditText.text.toString()
                previousStartTime = startTimeEditText.text.toString()
                intervalEditText.isEnabled = true
                startTimeEditText.isEnabled = true
                btnEditParams.text = getString(R.string.button_confirm)
            } else {
                dialogManager.showEditConfirmationDialog(
                    onConfirm = {
                        sendNewParamsToDevice()
                        isEditingMode = false
                        btnEditParams.text = getString(R.string.button_edit)
                        intervalEditText.isEnabled = false
                        startTimeEditText.isEnabled = false
                        LogStorageManager.logMessage("Режим редактирования выключен, изменения отправлены")
                    },
                    onCancel = {
                        intervalEditText.setText(previousInterval)
                        startTimeEditText.setText(previousStartTime)
                        isEditingMode = false
                        btnEditParams.text = getString(R.string.button_edit)
                        intervalEditText.isEnabled = false
                        startTimeEditText.isEnabled = false
                        LogStorageManager.logMessage("Режим редактирования выключен, изменения отменены")
                    }
                )
            }
        }

        intervalEditText.setOnClickListener {
            if (isEditingMode) {
                val currentText = intervalEditText.text.toString()
                val currentValue = ddHHMMToMinutes(currentText) ?: 1
                dialogManager.showIntervalPickerDialog(currentValue) { selectedValue ->
                    intervalEditText.setText(minutesToDDHHMM(selectedValue))
                }
            } else {
                Toast.makeText(this, R.string.toast_press_field, Toast.LENGTH_LONG).show()
            }
        }

        startTimeEditText.setOnClickListener {
            if (isEditingMode) {
                dialogManager.showStartTimePickerDialog { selectedTime ->
                    startTimeEditText.setText(selectedTime)
                }
            } else {
                Toast.makeText(this, R.string.toast_press_field, Toast.LENGTH_LONG).show()
            }
        }

        btnSyncTime.setOnClickListener {
            if (!bluetoothManager.isConnected()) {
                Toast.makeText(this, R.string.toast_connect_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialogManager.showSyncTimeConfirmationDialog {
                syncTime()
            }
        }

        btnDownloadStorage.setOnClickListener {
            Toast.makeText(this, "Функциональность пока не реализована (реальное устройство не существует)", Toast.LENGTH_SHORT).show()
            LogStorageManager.logMessage("Кнопка СКАЧАТЬ ДАННЫЕ ИЗ ХРАНИЛИЩА пока неактивна - реальное устройство не существует")
        }

        findViewById<ImageButton>(R.id.btnLogs).setOnClickListener {
            startActivity(Intent(this, LogDisplayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }

        findViewById<ImageButton>(R.id.btnHome).setOnClickListener {
            // Уже на главном экране
        }

        findViewById<ImageButton>(R.id.btnFiles).setOnClickListener {
            startActivity(Intent(this, StorageActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }

        setControlButtonsEnabled(false)
    }

    private fun setControlButtonsEnabled(enabled: Boolean) {
        btnEditParams.isEnabled = enabled
        btnSyncTime.isEnabled = enabled
        btnDownloadStorage.isEnabled = enabled
        // btnDownloadStorage оставлен выключенным - функциональность пока не реализована
    }

    private fun updateUIState(connected: Boolean) {
        val device = bluetoothManager.getConnectedDevice()
        if (connected) {
            tvDeviceStatus.text = getString(R.string.device_connected)
            tvDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.connected_green))
            tvDeviceName.text = device?.name ?: "Неизвестно"
            btnConnect.text = getString(R.string.disconnect_device)
            setControlButtonsEnabled(true)
            
            // Show hidden views
            tvDeviceNameLabel.visibility = android.view.View.VISIBLE
            tvDeviceName.visibility = android.view.View.VISIBLE
            intervalGroup.visibility = android.view.View.VISIBLE
            startTimeGroup.visibility = android.view.View.VISIBLE
            deviceTimeGroup.visibility = android.view.View.VISIBLE
            buttonGroup.visibility = android.view.View.VISIBLE
            
            // Start periodic time requests every 30 seconds
            startTimeRequestPeriodic()
        } else {
            tvDeviceStatus.text = getString(R.string.device_not_connected)
            tvDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.disconnected_red))
            tvDeviceName.text = ""
            btnConnect.text = getString(R.string.connect_device)
            setControlButtonsEnabled(false)
            
            // Hide hidden views
            tvDeviceNameLabel.visibility = android.view.View.GONE
            tvDeviceName.visibility = android.view.View.GONE
            intervalGroup.visibility = android.view.View.GONE
            startTimeGroup.visibility = android.view.View.GONE
            deviceTimeGroup.visibility = android.view.View.GONE
            buttonGroup.visibility = android.view.View.GONE
            
            // Reset fields to empty (no default values needed)
            intervalEditText.setText("")
            startTimeEditText.setText("")
            deviceTimeText.text = ""
            
            // Stop periodic time requests
            stopTimeRequestPeriodic()
        }
    }
    
    private fun startTimeRequestPeriodic() {
        stopTimeRequestPeriodic() // Ensure no duplicate runners
        timeRequestRunnable = object : Runnable {
            override fun run() {
                if (bluetoothManager.isConnected()) {
                    bluetoothManager.sendCommand(CMD_GET_TIME)
                    LogStorageManager.logMessage("Запрос текущего времени с устройства (периодический)")
                }
                handler.postDelayed(this, 30000) // 30 seconds
            }
        }
        handler.postDelayed(timeRequestRunnable!!, 30000)
    }
    
    private fun stopTimeRequestPeriodic() {
        timeRequestRunnable?.let {
            handler.removeCallbacks(it)
            timeRequestRunnable = null
        }
    }

    private fun checkAndRequestPermissions() {
        Log.d(TAG, "Checking permissions...")
        LogStorageManager.logMessage("Проверка разрешений...")
        
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        val hasPermissions = permissions.all { 
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED 
        }
        
        Log.d(TAG, "Has permissions: $hasPermissions")
        LogStorageManager.logMessage("Разрешения есть: $hasPermissions")

        if (hasPermissions) {
            checkBluetoothAndOpenDeviceList()
        } else {
            Log.d(TAG, "Launching permission request...")
            LogStorageManager.logMessage("Запуск запроса разрешений...")
            requestPermissionsLauncher.launch(permissions)
        }
    }

    private fun checkBluetoothAndOpenDeviceList() {
        val adapter = bluetoothManager.getAdapter()
        if (adapter == null) {
            Toast.makeText(this, R.string.toast_bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            return
        }

        @Suppress("MissingPermission")
        if (!adapter.isEnabled) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            openDeviceList()
        }
    }

    private fun openDeviceList() {
        try {
            val intent = Intent(this, DeviceListActivity::class.java)
            deviceListLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening device list: ${e.message}")
            Toast.makeText(this, R.string.toast_error_opening_list, Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendNewParamsToDevice() {
        val intervalText = intervalEditText.text.toString()
        val intervalMinutes = ddHHMMToMinutes(intervalText)
        val startTime = startTimeEditText.text.toString()

        if (intervalMinutes == null) {
            LogStorageManager.logMessage("Ошибка: некорректный формат интервала: $intervalText")
            Toast.makeText(this, "Некорректный формат интервала", Toast.LENGTH_SHORT).show()
            return
        }

        LogStorageManager.logMessage("Отправка параметров на устройство...")
        LogStorageManager.logMessage("Интервал (поле): $intervalText")
        LogStorageManager.logMessage("Интервал (минуты): $intervalMinutes")
        LogStorageManager.logMessage("Время старта: $startTime")
        
        // Отправляем команды с небольшой задержкой между ними
        bluetoothManager.sendCommand(CMD_SET_INTERVAL + intervalMinutes)
        LogStorageManager.logMessage("Команда: $CMD_SET_INTERVAL$intervalMinutes")
        
        handler.postDelayed({
            bluetoothManager.sendCommand(CMD_SET_START + startTime)
            LogStorageManager.logMessage("Команда: $CMD_SET_START$startTime")
        }, 100)
    }

    private fun syncTime() {
        val currentTime = SimpleDateFormat("HH:mm dd.MM.yyyy", Locale.getDefault()).format(Date())
        bluetoothManager.sendCommand(CMD_SET_TIME + currentTime)
        LogStorageManager.logMessage("Время синхронизировано: $currentTime")
        
        // Запрашиваем подтверждение времени после синхронизации
        handler.postDelayed({
            bluetoothManager.sendCommand(CMD_GET_TIME)
        }, 500)
    }

    private fun processResponse(response: String) {
        Log.d(TAG, "Received: $response")
        
        val timePattern = Regex("\\d{1,2}:\\d{2}(:\\d{2})?\\s+\\d{1,2}[\\.\\-]\\d{1,2}[\\.\\-]\\d{2,4}")
        
        // 1. Проверяем время старта (ищем время в ответе)
        if (response.contains("START", ignoreCase = true) || response.startsWith("S:")) {
            val timeMatch = timePattern.find(response)
            if (timeMatch != null) {
                val time = timeMatch.value
                startTimeEditText.setText(time)
                LogStorageManager.logMessage("Время старта обновлено: $time")
                return
            } else if (response.contains("OK", ignoreCase = true)) {
                // "OK START" без времени - просто подтверждение
                LogStorageManager.logMessage("Подтверждение: $response")
                return
            }
        }
        
        // 2. Проверяем текущее время (ищем время в ответе)
        if (response.contains("CURRENT", ignoreCase = true) || response.contains("TIME", ignoreCase = true) || response.startsWith("T:")) {
            val timeMatch = timePattern.find(response)
            if (timeMatch != null) {
                val time = timeMatch.value
                deviceTimeText.text = time
                LogStorageManager.logMessage("Текущее время обновлено: $time")
                return
            } else if (response.contains("OK", ignoreCase = true)) {
                // "OK TIME" без времени - просто подтверждение
                LogStorageManager.logMessage("Подтверждение: $response")
                return
            }
        }
        
        // 3. Проверяем интервал
        if (response.contains("INTERVAL", ignoreCase = true) || response.startsWith("I:")) {
            val numberMatch = Regex("\\d+").find(response)
            if (numberMatch != null) {
                val intervalMinutes = numberMatch.value.toIntOrNull()
                if (intervalMinutes != null) {
                    val currentInterval = ddHHMMToMinutes(intervalEditText.text.toString())
                    if (currentInterval == null || currentInterval != intervalMinutes) {
                        intervalEditText.setText(minutesToDDHHMM(intervalMinutes))
                        LogStorageManager.logMessage("Интервал обновлен: $intervalMinutes мин")
                    } else {
                        LogStorageManager.logMessage("Интервал не изменен (совпадает с текущим): $intervalMinutes мин")
                    }
                }
            } else if (response.contains("OK", ignoreCase = true)) {
                // "OK INTERVAL" без числа - просто подтверждение
                LogStorageManager.logMessage("Подтверждение: $response")
            }
            return
        }
        
        // 4. Если только число - считаем интервалом
        if (response.matches(Regex("\\d+"))) {
            val intervalMinutes = response.toIntOrNull()
            if (intervalMinutes != null) {
                intervalEditText.setText(minutesToDDHHMM(intervalMinutes))
                LogStorageManager.logMessage("Интервал обновлен (авто): $intervalMinutes мин")
            }
            return
        }
        
        // 5. Если ответ выглядит как время (без префиксов) - обновляем текущее время
        val timeMatch = timePattern.find(response)
        if (timeMatch != null) {
            val time = timeMatch.value
            deviceTimeText.text = time
            LogStorageManager.logMessage("Текущее время обновлено: $time")
            return
        }
        
        // 6. Общие подтверждения (OK, Установлено, Успешно)
        if (response.contains("OK", ignoreCase = true) || 
            response.contains("Установлен", ignoreCase = true) ||
            response.contains("Успешно", ignoreCase = true)) {
            LogStorageManager.logMessage("Подтверждение: $response")
            return
        }
        
        // 7. Для всех остальных ответов - просто логируем
        LogStorageManager.logMessage("Ответ от устройства: $response")
    }

    private fun displayData(data: String) {
        if (data.isBlank()) {
            LogStorageManager.logMessage("Данные: пусто")
        } else {
            LogStorageManager.logMessage("Данные получены: ${data.length} символов")
        }
    }

    // logMessage is now handled by LogManager

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called, shouldCheckPermissionsOnResume: $shouldCheckPermissionsOnResume")
        
        // Only auto-open device list if returning from settings with permissions granted
        if (!shouldCheckPermissionsOnResume) return
        
        shouldCheckPermissionsOnResume = false
        
        val hasBluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
        
        Log.d(TAG, "Has Bluetooth permissions: $hasBluetoothPermissions, Connected: ${bluetoothManager.isConnected()}")
        
        if (hasBluetoothPermissions && !bluetoothManager.isConnected()) {
            Log.d(TAG, "Auto-opening device list because permissions are granted")
            Handler(Looper.getMainLooper()).postDelayed({
                checkBluetoothAndOpenDeviceList()
            }, 500)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Не отключаем Bluetooth при навигации - соединение сохраняется через синглтон
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Обновляем UI при возврате на главный экран
        val wasConnected = bluetoothManager.isConnected()
        updateUIState(wasConnected)
        Log.d(TAG, "onNewIntent, wasConnected: $wasConnected")
    }
}