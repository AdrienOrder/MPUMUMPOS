package com.mobileapp.bluetooth

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mobileapp.log.LogStorageManager

@SuppressLint("MissingPermission")
class DeviceListActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DeviceListActivity"
    }

    private lateinit var lvDevices: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnRefresh: Button

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val deviceList = ArrayList<BluetoothDevice>()
    private val deviceNames = ArrayList<String>()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            loadDevices()
        } else {
            Toast.makeText(this, R.string.toast_permissions_needed, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogStorageManager.logMessage("Выбор устройства")

        try {
            setContentView(R.layout.activity_device_list)

            lvDevices = findViewById(R.id.lvDevices)
            progressBar = findViewById(R.id.progressBar)
            btnRefresh = findViewById(R.id.btnRefresh)

            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_2,
                android.R.id.text1,
                deviceNames
            )
            lvDevices.adapter = adapter

            lvDevices.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                if (position < deviceList.size) {
                    val device = deviceList[position]
                    LogStorageManager.logMessage("Выбрано устройство: ${device.name} (${device.address})")
                    val resultIntent = Intent().apply {
                        putExtra("device_address", device.address)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }

            btnRefresh.setOnClickListener {
                checkPermissionsAndLoadDevices()
            }

            @Suppress("MissingPermission")
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                Toast.makeText(this, R.string.toast_bluetooth_not_supported_device_list, Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            checkPermissionsAndLoadDevices()

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в onCreate: ${e.message}", e)
            Toast.makeText(this, R.string.toast_activity_error, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun checkPermissionsAndLoadDevices() {
        if (checkPermissions()) {
            loadDevices()
        } else {
            requestPermissions()
        }
    }

    private fun checkPermissions(): Boolean {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        return permissionsNeeded.isEmpty()
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)

        Log.d(TAG, "Запрашиваем разрешения: $permissionsToRequest")
        requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun loadDevices() {
        deviceList.clear()
        deviceNames.clear()
        progressBar.visibility = View.VISIBLE

        try {
            @Suppress("MissingPermission")
            if (bluetoothAdapter?.isEnabled != true) {
                LogStorageManager.logMessage("Bluetooth не включен, требуется включение")
                Toast.makeText(this, R.string.toast_enable_bluetooth, Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                return
            }

            val pairedDevices = try {
                bluetoothAdapter?.bondedDevices
            } catch (e: SecurityException) {
                LogStorageManager.logMessage("Ошибка безопасности при получении списка устройств: ${e.message}")
                Toast.makeText(this, "Нет разрешения для доступа к списку устройств", Toast.LENGTH_SHORT).show()
                null
            }

            pairedDevices?.forEach { device ->
                deviceList.add(device)

                val deviceName = try {
                    device.name ?: "Неизвестное устройство"
                } catch (e: SecurityException) {
                    "Неизвестное устройство"
                }

                deviceNames.add("$deviceName\n${device.address}")
            }

            @Suppress("UNCHECKED_CAST")
            (lvDevices.adapter as ArrayAdapter<String>).notifyDataSetChanged()
            progressBar.visibility = View.GONE

            if (deviceList.isEmpty()) {
                Toast.makeText(this, R.string.toast_no_paired_devices, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.toast_found_devices, deviceList.size), Toast.LENGTH_SHORT).show()
            }

        } catch (e: SecurityException) {
            progressBar.visibility = View.GONE
            Log.e(TAG, "SecurityException: ${e.message}", e)
            Toast.makeText(this, R.string.toast_permission_error, Toast.LENGTH_LONG).show()
            requestPermissions()
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Log.e(TAG, "Ошибка загрузки устройств: ${e.message}", e)
            Toast.makeText(this, getString(R.string.toast_error_loading_devices, e.message), Toast.LENGTH_SHORT).show()
        }
    }
}
