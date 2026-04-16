package com.mobileapp

import android.content.pm.ActivityInfo
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mobileapp.data.CsvDataParser
import com.mobileapp.data.CsvFileInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TableActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "table_prefs"
        private const val KEY_LANDSCAPE = "is_landscape"
        
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_SELECTED_PARAMS = "selected_params"
        const val EXTRA_FROM_DATE = "from_date"
        const val EXTRA_TO_DATE = "to_date"
        private const val MAX_ROWS = 1000
    }

    private lateinit var tableLayout: TableLayout
    private lateinit var progressBar: ProgressBar
    private var filePath: String = ""
    private var fileName: String = ""
    private var selectedParams: List<String> = emptyList()
    private var fromTimestamp: Long = 0
    private var toTimestamp: Long = 0
    private var isLandscape: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        isLandscape = prefs.getBoolean(KEY_LANDSCAPE, false)
        
        setContentView(R.layout.activity_table)
        setRequestedOrientation(if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
        fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: ""
        selectedParams = intent.getStringArrayListExtra(EXTRA_SELECTED_PARAMS) ?: emptyList()
        fromTimestamp = intent.getLongExtra(EXTRA_FROM_DATE, 0)
        toTimestamp = intent.getLongExtra(EXTRA_TO_DATE, 0)

        LogStorageManager.logMessage("=== ТАБЛИЦА: Открыт файл '$fileName' ===")
        LogStorageManager.logMessage("Таблица: Путь: $filePath")
        LogStorageManager.logMessage("Таблица: Параметры: $selectedParams")
        LogStorageManager.logMessage("Таблица: Период: ${formatDate(fromTimestamp)} - ${formatDate(toTimestamp)}")

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { 
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_LANDSCAPE, isLandscape).apply()
            LogStorageManager.logMessage("Таблица: Закрытие экрана")
            finish() 
        }
        
        findViewById<ImageButton>(R.id.btnRotate).setOnClickListener {
            isLandscape = !isLandscape
            requestedOrientation = if (isLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            setRequestedOrientation(requestedOrientation)
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_LANDSCAPE, isLandscape).apply()
            LogStorageManager.logMessage("Таблица: Поворот экрана в ${if (isLandscape) "гориз." else "верт."}")
        }
        
        tableLayout = findViewById(R.id.tableData)
        progressBar = findViewById(R.id.progressBar)
        
        setupTableAsync()
    }

    private fun formatDate(ts: Long): String {
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ts))
    }

    private fun setupTableAsync() {
        progressBar.visibility = View.VISIBLE
        
        Thread {
            val csvInfo = CsvDataParser.parseCsvFile(filePath)
            val dataParams = if (selectedParams.isEmpty()) csvInfo?.dataColumns ?: emptyList() else selectedParams
            val rows = buildTableRows(csvInfo, dataParams)
            
            mainHandler.post {
                progressBar.visibility = View.GONE
                tableLayout.removeAllViews()
                
                // Add header
                val headerRow = TableRow(this@TableActivity).apply {
                    setBackgroundColor(0xFFE3F2FD.toInt())
                }
                headerRow.addView(createCell("Дата/Время", true))
                for (param in dataParams) {
                    headerRow.addView(createCell(param, true))
                }
                tableLayout.addView(headerRow)
                
                // Add data rows
                rows.forEach { row ->
                    tableLayout.addView(row)
                }
                
                LogStorageManager.logMessage("Таблица: Отображено строк: ${rows.size}")
                LogStorageManager.logMessage("Таблица: Таблица построена успешно")
            }
        }.start()
    }

    private fun buildTableRows(csvInfo: CsvFileInfo?, dataParams: List<String>): List<TableRow> {
        val rows = mutableListOf<TableRow>()
        
        if (csvInfo == null || dataParams.isEmpty()) {
            LogStorageManager.logMessage("Таблица: ОШИБКА - нет данных")
            return rows
        }
        
        // Validate period
        if (fromTimestamp > 0 && toTimestamp > 0 && fromTimestamp > toTimestamp) {
            LogStorageManager.logMessage("Таблица: ОШИБКА - неверный период: ${formatDate(fromTimestamp)} > ${formatDate(toTimestamp)}")
            return rows
        }
        
        LogStorageManager.logMessage("Таблица: Период: ${formatDate(fromTimestamp)} - ${formatDate(toTimestamp)}")
        LogStorageManager.logMessage("Таблица: Всего точек в файле: ${csvInfo.dataPoints.size}")
        LogStorageManager.logMessage("Таблица: Столбцов: ${dataParams.size}")
        
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        var rowCount = 0
        var skipped = 0
        
        for (point in csvInfo.dataPoints) {
            if (rowCount >= MAX_ROWS) {
                LogStorageManager.logMessage("Таблица: Достигнут лимит $MAX_ROWS строк")
                break
            }
            
            val ts = point.timestamp
            if (fromTimestamp > 0 && ts < fromTimestamp) { skipped++; continue }
            if (toTimestamp > 0 && ts > toTimestamp) { skipped++; continue }
            
            val dataRow = TableRow(this).apply {
                setBackgroundColor(if (rowCount % 2 == 0) 0xFFFFFFFF.toInt() else 0xFFF5F5F5.toInt())
            }
            dataRow.addView(createCell(dateFormat.format(Date(ts)), false))

            for (param in dataParams) {
                val value = point.values[param]?.toString() ?: "-"
                dataRow.addView(createCell(value, false))
            }
            
            rows.add(dataRow)
            rowCount++
        }
        
        LogStorageManager.logMessage("Таблица: Пропущено строк: $skipped")
        return rows
    }

    private fun createCell(text: String, isHeader: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = if (isHeader) 13f else 12f
            setPadding(24, 16, 24, 16)
            setBackgroundColor(if (isHeader) 0xFFE3F2FD.toInt() else 0xFFFFFFFF.toInt())
            minWidth = 120
            if (isHeader) {
                setTextColor(0xFF1976D2.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
        }
    }
}
