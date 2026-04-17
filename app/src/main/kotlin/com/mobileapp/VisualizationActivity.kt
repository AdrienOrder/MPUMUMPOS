package com.mobileapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mobileapp.data.CsvDataParser
import com.mobileapp.data.CsvFileInfo
import com.mobileapp.data.Device
import com.mobileapp.data.ParamBounds
import com.mobileapp.LogDisplayActivity
import com.mobileapp.MainActivity
import com.mobileapp.StorageActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VisualizationActivity : AppCompatActivity() {

    private lateinit var rvParams: androidx.recyclerview.widget.RecyclerView
    private lateinit var btnFromDate: Button
    private lateinit var btnToDate: Button
    private lateinit var btnShowChart: Button
    private lateinit var btnShowTable: Button
    private lateinit var btnDay: Button
    private lateinit var btnMonth: Button
    private lateinit var btnYear: Button
    private lateinit var btnExportPdf: Button
    
    private var selectedDay: Long? = null
    private var selectedMonth: Long? = null
    private var selectedYear: Int? = null

    private var filePath: String = ""
    private var fileName: String = ""
    private var csvInfo: CsvFileInfo? = null
    private var paramBoundsList = mutableListOf<ParamWithBounds>()
    private var paramAdapter: ParamAdapter? = null
    private var chartIsVertical: Boolean = true

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
        btnDay = findViewById(R.id.btnDay)
        btnMonth = findViewById(R.id.btnMonth)
        btnYear = findViewById(R.id.btnYear)
        btnExportPdf = findViewById(R.id.btnExportPdf)

        setupParamsList()
        setupDatePickers()
        setupShowButton()
        setupShowTable()
        setupPeriodButtons()
        setupDayMonthYear()
        setupPdfExport()
        setupBottomNavigation()
    }

    private fun setupParamsList() {
        paramAdapter = ParamAdapter { param, isChecked, lower, upper ->
            val pb = paramBoundsList.find { it.name == param }
            if (pb != null) {
                pb.isSelected = isChecked
                pb.lowerBound = lower
                pb.upperBound = upper
                if (isChecked) {
                    LogStorageManager.logMessage("Визуализация: Выбран параметр: $param, границы: $lower - $upper")
                } else {
                    LogStorageManager.logMessage("Визуализация: Снят параметр: $param")
                }
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
            val selectedParams = paramBoundsList.filter { it.isSelected }
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
            
            for (pb in selectedParams) {
                if (pb.lowerBound != null && pb.upperBound != null && pb.lowerBound!! >= pb.upperBound!!) {
                    Toast.makeText(this, "Ошибка: нижняя граница >= верхней для ${pb.name}", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }
            
            LogStorageManager.logMessage("Визуализация: Открытие графика")
            LogStorageManager.logMessage("Визуализация: Параметры: ${selectedParams.map { "${it.name}(${it.lowerBound}-${it.upperBound})" }}")
            LogStorageManager.logMessage("Визуализация: Период: ${formatDate(fromTimestamp ?: 0)} - ${formatDate(toTimestamp ?: 0)}")
            
            val boundsList = selectedParams.mapNotNull { param ->
                val lb = param.lowerBound
                val ub = param.upperBound
                if (lb != null || ub != null) {
                    "${param.name}=${lb ?: ""},${ub ?: ""}"
                } else null
            }
            
            startActivity(Intent(this, ChartActivity::class.java).apply {
                putExtra("file_path", filePath)
                putExtra("file_name", fileName)
                putStringArrayListExtra("selected_params", ArrayList(selectedParams.map { it.name }))
                putExtra("from_date", fromTimestamp ?: 0)
                putExtra("to_date", toTimestamp ?: 0)
                putExtra("chart_vertical", if (chartIsVertical) 1 else 0)
                putStringArrayListExtra("param_bounds", ArrayList(boundsList))
            })
        }
    }

    private fun setupShowTable() {
        btnShowTable.setOnClickListener {
            val selectedParams = paramBoundsList.filter { it.isSelected }
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
            LogStorageManager.logMessage("Визуализация: Параметры: ${selectedParams.map { it.name }}")
            LogStorageManager.logMessage("Визуализация: Период: ${formatDate(fromTimestamp ?: 0)} - ${formatDate(toTimestamp ?: 0)}")
            startActivity(Intent(this, TableActivity::class.java).apply {
                putExtra("file_path", filePath)
                putExtra("file_name", fileName)
                putStringArrayListExtra("selected_params", ArrayList(selectedParams.map { it.name }))
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
        
        paramBoundsList = csvInfo!!.dataColumns.map { ParamWithBounds(it) }.toMutableList()
        paramAdapter?.submitList(paramBoundsList)
        
        val minTime = csvInfo!!.dataPoints.minOf { it.timestamp }
        val maxTime = csvInfo!!.dataPoints.maxOf { it.timestamp }
        btnFromDate.text = formatDate(minTime)
        btnToDate.text = formatDate(maxTime)
        fromTimestamp = minTime
        toTimestamp = maxTime
        
        LogStorageManager.logMessage("Визуализация: Диапазон дат файла: ${formatDate(minTime)} - ${formatDate(maxTime)}")
    }
    
    private fun setupDayMonthYear() {
        btnDay.setOnClickListener {
            val csvInfo = this.csvInfo ?: return@setOnClickListener
            val dayFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val uniqueDays = csvInfo.dataPoints
                .map { it.timestamp to dayFormat.format(Date(it.timestamp)) }
                .distinctBy { it.second }
                .sortedBy { it.first }
                .map { it.second }
            
            if (uniqueDays.isEmpty()) {
                Toast.makeText(this, "Нет данных", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            showSelectionDialog("Выберите день", uniqueDays) { selected ->
                selectedDay = dayFormat.parse(selected)?.time
                clearPeriodSelections()
                applyDaySelection(selected)
            }
        }
        
        btnMonth.setOnClickListener {
            val csvInfo = this.csvInfo ?: return@setOnClickListener
            val monthFormat = SimpleDateFormat("MM.yyyy", Locale.getDefault())
            val uniqueMonths = csvInfo.dataPoints
                .map { it.timestamp to monthFormat.format(Date(it.timestamp)) }
                .distinctBy { it.second }
                .sortedBy { it.first }
                .map { it.second }
            
            if (uniqueMonths.isEmpty()) {
                Toast.makeText(this, "Нет данных", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            showSelectionDialog("Выберите месяц", uniqueMonths) { selected ->
                val parseFormat = SimpleDateFormat("MM.yyyy", Locale.getDefault())
                selectedMonth = parseFormat.parse(selected)?.time
                clearPeriodSelections()
                applyMonthSelection(selected)
            }
        }
        
        btnYear.setOnClickListener {
            val csvInfo = this.csvInfo ?: return@setOnClickListener
            val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
            val uniqueYears = csvInfo.dataPoints
                .map { it.timestamp to yearFormat.format(Date(it.timestamp)) }
                .distinctBy { it.second }
                .sortedBy { it.first }
                .map { it.second }
            
            if (uniqueYears.isEmpty()) {
                Toast.makeText(this, "Нет данных", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            showSelectionDialog("Выберите год", uniqueYears) { selected ->
                selectedYear = selected.toIntOrNull()
                clearPeriodSelections()
                applyYearSelection(selected)
            }
        }
    }
    
    private fun clearPeriodSelections() {
        selectedDay = null
        selectedMonth = null
        selectedYear = null
    }
    
    private fun applyDaySelection(dayStr: String) {
        val csvInfo = this.csvInfo ?: return
        val dayFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val parseDate = dayFormat.parse(dayStr) ?: return
        val startOfDay = parseDate.time
        val endOfDay = startOfDay + 24 * 60 * 60 * 1000 - 1
        
        fromTimestamp = startOfDay
        toTimestamp = endOfDay
        btnFromDate.text = dayFormat.format(Date(startOfDay))
        btnToDate.text = dayFormat.format(Date(endOfDay))
        LogStorageManager.logMessage("Визуализация: Выбран день $dayStr")
    }
    
    private fun applyMonthSelection(monthStr: String) {
        val csvInfo = this.csvInfo ?: return
        val parseFormat = SimpleDateFormat("MM.yyyy", Locale.getDefault())
        val monthDate = parseFormat.parse(monthStr) ?: return
        val calendar = java.util.Calendar.getInstance()
        calendar.time = monthDate
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        val startOfMonth = calendar.timeInMillis
        calendar.add(java.util.Calendar.MONTH, 1)
        val endOfMonth = calendar.timeInMillis - 1
        
        fromTimestamp = startOfMonth
        toTimestamp = endOfMonth
        btnFromDate.text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(startOfMonth))
        btnToDate.text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(endOfMonth))
        LogStorageManager.logMessage("Визуализация: Выбран месяц $monthStr")
    }
    
    private fun applyYearSelection(yearStr: String) {
        val year = yearStr.toIntOrNull() ?: return
        val calendar = java.util.Calendar.getInstance()
        calendar.set(year, 0, 1, 0, 0, 0)
        val startOfYear = calendar.timeInMillis
        calendar.set(year + 1, 0, 1, 0, 0, 0)
        val endOfYear = calendar.timeInMillis - 1
        
        fromTimestamp = startOfYear
        toTimestamp = endOfYear
        btnFromDate.text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(startOfYear))
        btnToDate.text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(endOfYear))
        LogStorageManager.logMessage("Визуализация: Выбран год $yearStr")
    }
    
private fun showSelectionDialog(title: String, items: List<String>, onSelect: (String) -> Unit) {
        val builder = android.app.AlertDialog.Builder(this)
        
        val titleView = android.widget.TextView(this).apply {
            text = title
            setTextColor(0xFF1976D2.toInt())
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 40, 0, 20)
        }
        builder.setCustomTitle(titleView)
        
        val itemsArray = items.toTypedArray()
        builder.setItems(itemsArray) { dialog, which ->
            onSelect(itemsArray[which])
        }
        
        builder.setNegativeButton("Отмена") { db, _ ->
            db.dismiss()
        }
        
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF1976D2.toInt())
        }
        dialog.show()
    }

    private fun formatDate(ts: Long): String {
        return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ts))
    }
    
    private fun setupPdfExport() {
        btnExportPdf.setOnClickListener {
            val selParams = paramBoundsList.filter { it.isSelected }
            if (selParams.isEmpty()) {
                Toast.makeText(this, "Выберите параметры", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            try {
                val csvInfo = this.csvInfo ?: run {
                    Toast.makeText(this, "Нет данных", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                val chart = com.github.mikephil.charting.charts.LineChart(this).apply {
                    description.isEnabled = false
                    setTouchEnabled(false)
                    isDragEnabled = false
                    setScaleEnabled(false)
                    isScaleXEnabled = false
                    isScaleYEnabled = false
                    setPinchZoom(false)
                    setDrawGridBackground(true)
                    setGridBackgroundColor(android.graphics.Color.WHITE)
                    isHighlightPerDragEnabled = false
                    
                    legend.apply {
                        textColor = android.graphics.Color.BLACK
                        textSize = 35f
                        isWordWrapEnabled = true
                        orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                        verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
                        horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
                        setDrawInside(false)
                        form = com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE
                        formSize = 25f
                        xEntrySpace = 40f
                        yEntrySpace = 20f
                        formToTextSpace = 15f
                    }
                    
                    xAxis.apply {
                        position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                        setDrawGridLines(true)
                        gridColor = android.graphics.Color.LTGRAY
                        textColor = android.graphics.Color.DKGRAY
                        textSize = 30f
                        granularity = 1f
                        setLabelRotationAngle(-90f)
                        labelCount = 10
                        valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                            private val format = java.text.SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
                            override fun getFormattedValue(value: Float): String {
                                return try { format.format(Date(value.toLong())) } catch (e: Exception) { "" }
                            }
                        }
                    }
                    
                    axisLeft.apply {
                        setDrawGridLines(true)
                        gridColor = android.graphics.Color.LTGRAY
                        textColor = android.graphics.Color.DKGRAY
                        textSize = 40f
                    }
                    axisRight.isEnabled = false
                }
                
                val dataSets = mutableListOf<com.github.mikephil.charting.data.LineDataSet>()
                val colors = listOf(
                    0xFFE91E63.toInt(), 0xFF2196F3.toInt(), 0xFF4CAF50.toInt(),
                    0xFFFF9800.toInt(), 0xFF9C27B0.toInt(), 0xFF00BCD4.toInt(),
                    0xFF795548.toInt(), 0xFF607D8B.toInt(), 0xFFFFEB3B.toInt(),
                    0xFF3F51B5.toInt(), 0xFF009688.toInt(), 0xFFF44336.toInt(),
                    0xFF8BC34A.toInt(), 0xFF673AB7.toInt(), 0xFF03A9F4.toInt(),
                    0xFFC2185B.toInt(), 0xFF1976D2.toInt(), 0xFF388E3C.toInt(),
                    0xFFFF5722.toInt(), 0xFF7B1FA2.toInt()
                )
                
                for ((index, param) in selParams.withIndex()) {
                    val paramName = param.name
                    val entries = mutableListOf<com.github.mikephil.charting.data.Entry>()
                    var lastEntry: com.github.mikephil.charting.data.Entry? = null
                    
                    for (point in csvInfo.dataPoints) {
                        val ts = point.timestamp
                        if (fromTimestamp != null && ts < fromTimestamp!!) continue
                        if (toTimestamp != null && ts > toTimestamp!!) continue
                        point.values[paramName]?.let { value ->
                            val entry = com.github.mikephil.charting.data.Entry(ts.toFloat(), value.toFloat())
                            entries.add(entry)
                            lastEntry = entry
                        }
                    }
                    
                    if (entries.isNotEmpty()) {
                        val color = colors[index % colors.size]
                        
                        val dataSet = com.github.mikephil.charting.data.LineDataSet(entries, paramName).apply {
                            this.color = color
                            lineWidth = 5f
                            setDrawCircles(true)
                            circleRadius = 8f
                            setDrawCircleHole(true)
                            circleHoleRadius = 4f
                            circleColors = listOf(color)
                            setDrawValues(false)
                            mode = com.github.mikephil.charting.data.LineDataSet.Mode.LINEAR
                        }
                        dataSets.add(dataSet)
                    }
                }
                
                if (dataSets.isEmpty()) {
                    Toast.makeText(this, "Нет данных", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                chart.data = com.github.mikephil.charting.data.LineData(dataSets.toList())
                chart.invalidate()
                
                val pdfDoc = android.graphics.pdf.PdfDocument()
                
                val pdfWidth = 2970
                val pdfHeight = 2100
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pdfWidth, pdfHeight, 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas
                
                val titlePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 80f
                    isFakeBoldText = true
                }
                canvas.drawText("Графики данных", 100f, 120f, titlePaint)
                
                val chartLeft = 100
                val chartTop = 300
                val chartW = pdfWidth - 200
                val chartH = pdfHeight - 450
                
                canvas.translate(chartLeft.toFloat(), chartTop.toFloat())
                chart.layout(0, 0, chartW, chartH)
                chart.draw(canvas)
                
                pdfDoc.finishPage(page)
                
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val mpumFolder = File(downloadsDir, "MPUMUMPOS Downloads")
                if (!mpumFolder.exists()) mpumFolder.mkdirs()
                
                val safeFileName = fileName.replace(".csv", "").replace(" ", "_")
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val pdfFileName = "${safeFileName}_$timestamp.pdf"
                val file = File(mpumFolder, pdfFileName)
                
                FileOutputStream(file).use { out ->
                    pdfDoc.writeTo(out)
                }
                pdfDoc.close()
                
                Toast.makeText(this, "Сохранено: ${file.name}", Toast.LENGTH_LONG).show()
                LogStorageManager.logMessage("Визуализация: PDF сохранен ${file.absolutePath}")
                
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                LogStorageManager.logMessage("Визуализация: Ошибка PDF: ${e.message}")
            }
        }
    }
    
    private fun setupBottomNavigation() {
        findViewById<View>(R.id.btnLogs)?.setOnClickListener {
            startActivity(Intent(this, LogDisplayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
        
        findViewById<View>(R.id.btnHome)?.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
        
        findViewById<View>(R.id.btnStorage)?.setOnClickListener {
            startActivity(Intent(this, StorageActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
    }
}