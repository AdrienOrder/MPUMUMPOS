package com.mobileapp

import android.graphics.Color
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.mobileapp.data.CsvDataParser
import com.mobileapp.data.CsvFileInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChartActivity : AppCompatActivity() {

    companion object {
        private val CHART_COLORS = listOf(
            0xFFE91E63.toInt(), 0xFF2196F3.toInt(), 0xFF4CAF50.toInt(),
            0xFFFF9800.toInt(), 0xFF9C27B0.toInt(), 0xFF00BCD4.toInt(),
            0xFF795548.toInt(), 0xFF607D8B.toInt()
        )
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_SELECTED_PARAMS = "selected_params"
        const val EXTRA_FROM_DATE = "from_date"
        const val EXTRA_TO_DATE = "to_date"
    }

    private lateinit var chart: LineChart
    private var filePath: String = ""
    private var fileName: String = ""
    private var selectedParams: List<String> = emptyList()
    private var fromTimestamp: Long = 0
    private var toTimestamp: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chart)
        
        filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
        fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: ""
        selectedParams = intent.getStringArrayListExtra(EXTRA_SELECTED_PARAMS) ?: emptyList()
        fromTimestamp = intent.getLongExtra(EXTRA_FROM_DATE, 0)
        toTimestamp = intent.getLongExtra(EXTRA_TO_DATE, 0)

        LogStorageManager.logMessage("=== ГРАФИК: Открыт файл '$fileName' ===")
        LogStorageManager.logMessage("График: Путь: $filePath")
        LogStorageManager.logMessage("График: Параметры: $selectedParams")
        LogStorageManager.logMessage("График: Период: ${formatDate(fromTimestamp)} - ${formatDate(toTimestamp)}")

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { 
            LogStorageManager.logMessage("График: Закрытие экрана")
            finish() 
        }
        
        chart = findViewById(R.id.chartFull)
        setupChart()
        drawChart()
    }

    private fun formatDate(ts: Long): String {
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ts))
    }

    private fun setupChart() {
        chart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            isScaleXEnabled = true
            isScaleYEnabled = true
            setPinchZoom(true)
            setDrawGridBackground(true)
            setGridBackgroundColor(Color.WHITE)
            isHighlightPerDragEnabled = true
            
            legend.apply {
                textColor = Color.BLACK
                textSize = 11f
                isWordWrapEnabled = true
                orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
                setDrawInside(false)
                form = com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE
                formSize = 8f
                xEntrySpace = 12f
                yEntrySpace = 6f
                formToTextSpace = 4f
            }
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
                textColor = Color.DKGRAY
                textSize = 11f
                granularity = 1f
                setLabelRotationAngle(-90f)
                labelCount = 10
                valueFormatter = object : ValueFormatter() {
                    private val format = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    override fun getFormattedValue(value: Float): String {
                        return try { format.format(Date(value.toLong())) } catch (e: Exception) { "" }
                    }
                }
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
                textColor = Color.DKGRAY
                textSize = 12f
                axisMinimum = 0f
                labelCount = 12
            }
            axisRight.isEnabled = false
            
            setNoDataText("Нет данных для отображения")
        }
    }

    private fun drawChart() {
        val csvInfo = CsvDataParser.parseCsvFile(filePath)
        if (csvInfo == null) {
            LogStorageManager.logMessage("График: ОШИБКА - файл не найден")
            return
        }
        if (selectedParams.isEmpty()) {
            LogStorageManager.logMessage("График: ОШИБКА - не выбраны параметры")
            return
        }
        
        // Validate period
        if (fromTimestamp > 0 && toTimestamp > 0 && fromTimestamp > toTimestamp) {
            LogStorageManager.logMessage("График: ОШИБКА - неверный период: ${formatDate(fromTimestamp)} > ${formatDate(toTimestamp)}")
            return
        }
        
        LogStorageManager.logMessage("График: Период: ${formatDate(fromTimestamp)} - ${formatDate(toTimestamp)}")
        LogStorageManager.logMessage("График: Построение графика...")
        LogStorageManager.logMessage("График: Всего точек в файле: ${csvInfo.dataPoints.size}")
        
        val dataSets = mutableListOf<LineDataSet>()
        var colorIndex = 0
        
        for (param in selectedParams) {
            val entries = mutableListOf<Entry>()
            var skippedBefore = 0
            var skippedAfter = 0
            for (point in csvInfo.dataPoints) {
                val ts = point.timestamp
                if (fromTimestamp > 0 && ts < fromTimestamp) { skippedBefore++; continue }
                if (toTimestamp > 0 && ts > toTimestamp) { skippedAfter++; continue }
                point.values[param]?.let { entries.add(Entry(ts.toFloat(), it.toFloat())) }
            }
            
            if (entries.isNotEmpty()) {
                val color = CHART_COLORS[colorIndex % CHART_COLORS.size]
                val dataSet = LineDataSet(entries, param).apply {
                    this.color = color
                    lineWidth = 2.5f
                    setDrawCircles(false)
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.LINEAR
                }
                dataSets.add(dataSet)
                LogStorageManager.logMessage("График: Параметр '$param' - точек: ${entries.size} (пропущено до: $skippedBefore, после: $skippedAfter)")
                colorIndex++
            } else {
                LogStorageManager.logMessage("График: Параметр '$param' - нет данных в периоде")
            }
        }
        
        if (dataSets.isEmpty()) {
            LogStorageManager.logMessage("График: ОШИБКА - нет данных для отображения в периоде")
            return
        }
        
        LogStorageManager.logMessage("График: Построено линий: ${dataSets.size}")
        
        chart.data = LineData(dataSets.toList())
        
        val xMin = dataSets.first().xMin
        val xMax = dataSets.first().xMax
        val xRange = xMax - xMin
        
        LogStorageManager.logMessage("График: Диапазон X: ${formatDate(xMin.toLong())} - ${formatDate(xMax.toLong())}")
        
        chart.post {
            chart.setVisibleXRangeMaximum(xRange)
            chart.setVisibleXRangeMinimum(xRange / 1000)
            chart.moveViewToX(xMin)
        }
        
        LogStorageManager.logMessage("График: График построен успешно")
    }
}