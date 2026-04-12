package com.mobileapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mobileapp.data.CsvDataParser
import com.mobileapp.data.CsvFileInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VisualizationActivity : AppCompatActivity() {

    private lateinit var rvParams: androidx.recyclerview.widget.RecyclerView
    private lateinit var btnFromDate: Button
    private lateinit var btnToDate: Button
    private lateinit var btnShowChart: Button
    private lateinit var btnDay: Button
    private lateinit var btnMonth: Button
    private lateinit var btnYear: Button
    private lateinit var btnShowTable: Button

    private var filePath: String = ""
    private var fileName: String = ""
    private var csvInfo: CsvFileInfo? = null
    private var selectedParams = mutableSetOf<String>()
    private var paramAdapter: ParamAdapter? = null

    private var fromTimestamp: Long? = null
    private var toTimestamp: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_visualization)

        filePath = intent.getStringExtra("file_path") ?: ""
        fileName = intent.getStringExtra("file_name") ?: ""

        LogStorageManager.logMessage("=== ВИЗУАЛИЗАЦИЯ: Открыт файл '$fileName' ===")
        LogStorageManager.logMessage("Визуализация: Путь файла: $filePath")

        initViews()
        loadCsv()
    }

    private fun initViews() {
        findViewById<TextView>(R.id.tvTitle).text = getString(R.string.viz_title)
        findViewById<TextView>(R.id.tvFileName).text = fileName

        rvParams = findViewById(R.id.rvParams)
        btnFromDate = findViewById(R.id.btnFromDate)
        btnToDate = findViewById(R.id.btnToDate)
        btnShowChart = findViewById(R.id.btnShowChart)
        btnShowTable = findViewById(R.id.btnShowTable)

        setupParamsList()
        setupDatePickers()
        setupShowButton()
        setupShowTable()
        setupPeriodButtons()
    }

    private fun setupParamsList() {
        paramAdapter = ParamAdapter { param, isChecked ->
            if (isChecked) {
                selectedParams.add(param)
                LogStorageManager.logMessage("Визуализация: Выбран параметр: $param")
            } else {
                selectedParams.remove(param)
                LogStorageManager.logMessage("Визуализация: Снят параметр: $param")
            }
        }
        rvParams.apply {
            layoutManager = LinearLayoutManager(this@VisualizationActivity)
            adapter = paramAdapter
        }
    }

    private fun setupDatePickers() {
        btnFromDate.setOnClickListener {
            LogStorageManager.logMessage("Визуализация: Открыт выбор даты ОТ")
            showDatePickerDialog(fromTimestamp ?: System.currentTimeMillis()) { ts ->
                fromTimestamp = ts
                btnFromDate.text = formatDate(ts)
                LogStorageManager.logMessage("Визуализация: Установлена дата ОТ: ${formatDate(ts)}")
            }
        }
        btnToDate.setOnClickListener {
            LogStorageManager.logMessage("Визуализация: Открыт выбор даты ДО")
            showDatePickerDialog(toTimestamp ?: System.currentTimeMillis()) { ts ->
                toTimestamp = ts
                btnToDate.text = formatDate(ts)
                LogStorageManager.logMessage("Визуализация: Установлена дата ДО: ${formatDate(ts)}")
            }
        }
    }

    private fun showDatePickerDialog(initialTimestamp: Long, onSelected: (Long) -> Unit) {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = initialTimestamp }
        
        val pickerDay = NumberPicker(this).apply {
            minValue = 1
            maxValue = 31
            value = calendar.get(java.util.Calendar.DAY_OF_MONTH)
            wrapSelectorWheel = false
        }
        
        val pickerMonth = NumberPicker(this).apply {
            minValue = 1
            maxValue = 12
            value = calendar.get(java.util.Calendar.MONTH) + 1
            wrapSelectorWheel = false
        }
        
        val pickerYear = NumberPicker(this).apply {
            minValue = 1900
            maxValue = 2199
            value = calendar.get(java.util.Calendar.YEAR)
            wrapSelectorWheel = false
        }

        fun getMaxDays(month: Int, year: Int): Int {
            return when (month) {
                2 -> if (year % 4 == 0) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }
        }

        pickerMonth.setOnValueChangedListener { _, _, newMonth ->
            pickerDay.maxValue = getMaxDays(newMonth, pickerYear.value)
        }
        pickerYear.setOnValueChangedListener { _, _, newYear ->
            pickerDay.maxValue = getMaxDays(pickerMonth.value, newYear)
        }
        pickerDay.maxValue = getMaxDays(pickerMonth.value, pickerYear.value)

        val labelDay = TextView(this).apply {
            text = "ДД"; textSize = 12f; gravity = android.view.Gravity.CENTER
            setTextColor(0xFF1976D2.toInt())
        }
        val labelMonth = TextView(this).apply {
            text = "ММ"; textSize = 12f; gravity = android.view.Gravity.CENTER
            setTextColor(0xFF1976D2.toInt())
        }
        val labelYear = TextView(this).apply {
            text = "ГГГГ"; textSize = 12f; gravity = android.view.Gravity.CENTER
            setTextColor(0xFF1976D2.toInt())
        }

        val row1 = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER
        }

        val addSeparator: (String) -> Unit = { char ->
            val sep = TextView(this).apply {
                text = char; textSize = 20f; gravity = android.view.Gravity.CENTER
                setTextColor(0xFF1976D2.toInt())
            }
            row1.addView(sep)
        }

        fun addPickerToRow(picker: NumberPicker, label: TextView, row: android.widget.LinearLayout) {
            val container = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            container.addView(label)
            container.addView(picker)
            row.addView(container)
        }

        addPickerToRow(pickerDay, labelDay, row1)
        addSeparator(".")
        addPickerToRow(pickerMonth, labelMonth, row1)
        addSeparator(".")
        addPickerToRow(pickerYear, labelYear, row1)

        val titleView = TextView(this).apply {
            text = "Выберите дату"; setTextColor(0xFF1976D2.toInt()); textSize = 18f
            gravity = android.view.Gravity.CENTER; setPadding(0, 24, 0, 16)
        }

        val dialogContainer = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }
        dialogContainer.addView(titleView)
        dialogContainer.addView(row1)

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogContainer)
            .setPositiveButton("OK") { _, _ ->
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, pickerYear.value)
                    set(java.util.Calendar.MONTH, pickerMonth.value - 1)
                    set(java.util.Calendar.DAY_OF_MONTH, pickerDay.value)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                }
                onSelected(cal.timeInMillis)
            }
            .setNegativeButton("Отмена", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
            positiveButton.setTextColor(0xFF1976D2.toInt())
            negativeButton.setTextColor(0xFF1976D2.toInt())
            val buttonPanel = positiveButton.parent as? android.widget.LinearLayout
            buttonPanel?.gravity = android.view.Gravity.CENTER
        }
        dialog.show()
    }

    private fun setupShowButton() {
        btnShowChart.setOnClickListener {
            if (selectedParams.isEmpty()) {
                LogStorageManager.logMessage("Визуализация: ОШИБКА - не выбраны параметры для графика")
                Toast.makeText(this, R.string.viz_select_params, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (fromTimestamp != null && toTimestamp != null && fromTimestamp!! > toTimestamp!!) {
                LogStorageManager.logMessage("Визуализация: ОШИБКА - неверный период")
                Toast.makeText(this, "Неверный период", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            LogStorageManager.logMessage("Визуализация: Открытие графика")
            LogStorageManager.logMessage("Визуализация: Параметры: $selectedParams")
            LogStorageManager.logMessage("Визуализация: Период: ${formatDate(fromTimestamp ?: 0)} - ${formatDate(toTimestamp ?: 0)}")
            startActivity(Intent(this, ChartActivity::class.java).apply {
                putExtra("file_path", filePath)
                putExtra("file_name", fileName)
                putStringArrayListExtra("selected_params", ArrayList(selectedParams))
                putExtra("from_date", fromTimestamp ?: 0)
                putExtra("to_date", toTimestamp ?: 0)
            })
        }
    }

    private fun setupShowTable() {
        btnShowTable.setOnClickListener {
            if (selectedParams.isEmpty()) {
                LogStorageManager.logMessage("Визуализация: ОШИБКА - не выбраны параметры для таблицы")
                Toast.makeText(this, R.string.viz_select_params, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (fromTimestamp != null && toTimestamp != null && fromTimestamp!! > toTimestamp!!) {
                LogStorageManager.logMessage("Визуализация: ОШИБКА - неверный период")
                Toast.makeText(this, "Неверный период", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            LogStorageManager.logMessage("Визуализация: Открытие таблицы")
            LogStorageManager.logMessage("Визуализация: Параметры: $selectedParams")
            LogStorageManager.logMessage("Визуализация: Период: ${formatDate(fromTimestamp ?: 0)} - ${formatDate(toTimestamp ?: 0)}")
            startActivity(Intent(this, TableActivity::class.java).apply {
                putExtra("file_path", filePath)
                putExtra("file_name", fileName)
                putStringArrayListExtra("selected_params", ArrayList(selectedParams))
                putExtra("from_date", fromTimestamp ?: 0)
                putExtra("to_date", toTimestamp ?: 0)
            })
        }
    }

    private fun setupPeriodButtons() {
        val info = csvInfo ?: return
        val allTimestamps = info.dataPoints.map { it.timestamp }.sorted()
        
        btnDay.setOnClickListener {
            LogStorageManager.logMessage("Визуализация: Выбор периода ДЕНЬ")
            showPeriodPicker("Выберите день (начало)", allTimestamps) { startTs ->
                val endTs = startTs + 24*60*60*1000L
                fromTimestamp = startTs
                toTimestamp = endTs
                btnFromDate.text = formatDate(startTs)
                btnToDate.text = formatDate(endTs)
                LogStorageManager.logMessage("Визуализация: Установлен день: ${formatDate(startTs)} - ${formatDate(endTs)}")
            }
        }
        
        btnMonth.setOnClickListener {
            LogStorageManager.logMessage("Визуализация: Выбор периода МЕСЯЦ")
            val minTs = allTimestamps.first()
            val maxTs = allTimestamps.last()
            showPeriodPicker("Выберите начало месяца", allTimestamps) { startTs ->
                val startCal = java.util.Calendar.getInstance().apply { timeInMillis = startTs }
                startCal.add(java.util.Calendar.MONTH, 1)
                val endTs = startCal.timeInMillis.coerceAtMost(maxTs)
                fromTimestamp = startTs
                toTimestamp = endTs
                btnFromDate.text = formatDate(startTs)
                btnToDate.text = formatDate(endTs)
                LogStorageManager.logMessage("Визуализация: Установлен месяц: ${formatDate(startTs)} - ${formatDate(endTs)}")
            }
        }
        
        btnYear.setOnClickListener {
            LogStorageManager.logMessage("Визуализация: Выбор периода ГОД")
            val minTs = allTimestamps.first()
            val maxTs = allTimestamps.last()
            fromTimestamp = minTs
            toTimestamp = maxTs
            btnFromDate.text = formatDate(minTs)
            btnToDate.text = formatDate(maxTs)
            LogStorageManager.logMessage("Визуализация: Установлен год: ${formatDate(minTs)} - ${formatDate(maxTs)}")
        }
    }
    
    private fun showPeriodPicker(title: String, timestamps: List<Long>, onSelected: (Long) -> Unit) {
        val picker = NumberPicker(this).apply {
            minValue = 1
            maxValue = timestamps.size
            value = 1
            wrapSelectorWheel = false
        }
        
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(picker)
            .setPositiveButton("OK") { _, _ ->
                if (picker.value in 1..timestamps.size) {
                    onSelected(timestamps[picker.value - 1])
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun loadCsv() {
        csvInfo = CsvDataParser.parseCsvFile(filePath)
        if (csvInfo == null || csvInfo!!.dataPoints.isEmpty()) {
            LogStorageManager.logMessage("Визуализация: ОШИБКА - файл пустой или не найден")
            Toast.makeText(this, R.string.viz_no_data, Toast.LENGTH_SHORT).show()
            return
        }
        
        LogStorageManager.logMessage("Визуализация: CSV загружен успешно")
        LogStorageManager.logMessage("Визуализация: Столбцов данных: ${csvInfo!!.dataColumns.size}")
        LogStorageManager.logMessage("Визуализация: Строк данных: ${csvInfo!!.dataPoints.size}")
        LogStorageManager.logMessage("Визуализация: Параметры: ${csvInfo!!.dataColumns}")
        
        paramAdapter?.submitList(csvInfo!!.dataColumns)
        val minTime = csvInfo!!.dataPoints.minOf { it.timestamp }
        val maxTime = csvInfo!!.dataPoints.maxOf { it.timestamp }
        btnFromDate.text = formatDate(minTime)
        btnToDate.text = formatDate(maxTime)
        fromTimestamp = minTime
        toTimestamp = maxTime
        
        LogStorageManager.logMessage("Визуализация: Диапазон дат файла: ${formatDate(minTime)} - ${formatDate(maxTime)}")
    }

    private fun formatDate(ts: Long): String {
        return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ts))
    }
}