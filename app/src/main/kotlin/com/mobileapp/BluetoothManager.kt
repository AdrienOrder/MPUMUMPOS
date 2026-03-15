package com.mobileapp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothManager(
    private val handler: Handler,
    private val onConnected: (BluetoothDevice) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onMessageReceived: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "BluetoothManager"
        private val HC05_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: BufferedReader? = null
    private var readThread: Thread? = null
    private var isConnected = false
    private var connectedDevice: BluetoothDevice? = null

    init {
        @Suppress("MissingPermission")
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    }

    fun getAdapter(): BluetoothAdapter? = bluetoothAdapter

    @SuppressLint("MissingPermission")
    fun connect(deviceAddress: String) {
        val adapter = bluetoothAdapter ?: run {
            handler.post { onError("Bluetooth adapter not found") }
            return
        }

        val device = adapter.getRemoteDevice(deviceAddress) ?: run {
            handler.post { onError("Device not found") }
            return
        }

        Thread {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(HC05_UUID)
                if (bluetoothSocket == null) {
                    throw IOException("Failed to create socket")
                }
                LogStorageManager.logMessage("Попытка подключения к ${device.name}...")
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                inputStream = BufferedReader(InputStreamReader(bluetoothSocket?.inputStream))
                
                if (outputStream == null || inputStream == null) {
                    throw IOException("Failed to get streams")
                }
                
                isConnected = true
                connectedDevice = device

                handler.post { onConnected(device) }
                startReading()
            } catch (e: IOException) {
                Log.e(TAG, "Connection error: ${e.message}")
                LogStorageManager.logMessage("Ошибка подключения: ${e.message}")
                handler.post { onError("Ошибка подключения: ${e.message}") }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security error: ${e.message}")
                LogStorageManager.logMessage("Ошибка безопасности (нет разрешений): ${e.message}")
                handler.post { onError("Ошибка безопасности: нет разрешений для доступа к Bluetooth") }
            }
        }.start()
    }

    private fun startReading() {
        readThread = Thread {
            try {
                while (isConnected && !Thread.currentThread().isInterrupted) {
                    val line = inputStream?.readLine()
                if (line != null) {
                    handler.post { onMessageReceived(line) }
                }
                }
            } catch (e: IOException) {
                if (isConnected) {
                    // Соединение было активно, но прервалось - это нормально при отключении
                    Log.d(TAG, "Соединение разорвано: ${e.message}")
                    disconnect()
                }
            }
        }
        readThread?.start()
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            LogStorageManager.logMessage("Закрытие соединения...")
            bluetoothSocket?.close()
            readThread?.interrupt()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket: ${e.message}")
            LogStorageManager.logMessage("Ошибка при закрытии соединения: ${e.message}")
        } finally {
            isConnected = false
            connectedDevice = null
            handler.post { onDisconnected() }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(command: String) {
        try {
            // Append CRLF newline character as many Bluetooth devices expect it
            val commandWithNewline = if (command.endsWith("\r\n") || command.endsWith("\n")) command else "$command\r\n"
            Log.d(TAG, "Отправка команды: $commandWithNewline")
            LogStorageManager.logMessage("Отправлено: ${commandWithNewline.trim()}")
            outputStream?.write(commandWithNewline.toByteArray())
            outputStream?.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Send error: ${e.message}")
            LogStorageManager.logMessage("Ошибка отправки: ${e.message}")
            handler.post { onError("Ошибка отправки: ${e.message}") }
        }
    }

    fun isConnected(): Boolean = isConnected
    fun getConnectedDevice(): BluetoothDevice? = connectedDevice
}
