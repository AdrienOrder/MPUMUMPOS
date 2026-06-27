package com.mobileapp.visualization

import android.content.pm.ActivityInfo
import android.content.SharedPreferences
import android.content.Intent
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.mobileapp.csv.CsvDataParser
import com.mobileapp.log.LogStorageManager
import com.mobileapp.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChartActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "chart_prefs"
        private const val KEY_LANDSCAPE = "is_landscape"

        private val CHART_COLORS = listOf(
            0xFFE91E63.toInt(), 0xFF2196F3.toInt(), 0xFF4CAF50.toInt(),
            0xFFFF9800.toInt(), 0xFF9C27B0.toInt(), 0xFF00BCD4.toInt(),
            0xFF795548.toInt(), 0xFF607D8B.toInt(), 0xFFFFEB3B.toInt(),
            0xFF3F51B5.toInt(), 0xFF009688.toInt(), 0xFFF44336.toInt(),
            0xFF8BC34A.toInt(), 0xFF673AB7.toInt(), 0xFF03A9F4.toInt(),
            0xFFC2185B.toInt(), 0xFF1976D2.toInt(), 0xFF388E3C.toInt(),
            0xFFFF5722.toInt(), 0xFF7B1FA2.toInt()
        )
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_SELECTED_PARAMS = "selected_params"
        const val EXTRA_FROM_DATE = "from_date"
        const val EXTRA_TO_DATE = "to_date"
        const val EXTRA_CHART_VERTICAL = "chart_vertical"
        const val EXTRA_PARAM_BOUNDS = "param_bounds"
    }

    private lateinit var chart: LineChart
    private var filePath: String = ""
    private var fileName: String = ""
    private var selectedParams: List<String> = emptyList()
    private var fromTimestamp: Long = 0
    private var toTimestamp: Long = 0
    private var chartIsVertical: Boolean = false
    private var isLandscape: Boolean = false
    private var paramBounds: Map<String, Pair<Double?, Double?>> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        isLandscape = prefs.getBoolean(KEY_LANDSCAPE, false)

        setContentView(R.layout.activity_chart)
        setRequestedOrientation(if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
        fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: ""
        selectedParams = intent.getStringArrayListExtra(EXTRA_SELECTED_PARAMS) ?: emptyList()
        fromTimestamp = intent.getLongExtra(EXTRA_FROM_DATE, 0)
        toTimestamp = intent.getLongExtra(EXTRA_TO_DATE, 0)

        val boundsStr = intent.getStringArrayListExtra(EXTRA_PARAM_BOUNDS)
        paramBounds = boundsStr?.associate { entry ->
            val parts = entry.split("=")
            val paramName = parts[0]
            val bounds = if (parts.size > 1 && parts[1].isNotEmpty()) {
                val bParts = parts[1].split(",")
                val lower = bParts.getOrNull(0)?.toDoubleOrNull()
                val upper = bParts.getOrNull(1)?.toDoubleOrNull()
                Pair(lower, upper)
            } else {
                Pair(null, null)
            }
            paramName to bounds
        } ?: emptyMap()

        LogStorageManager.logMessage("График: $fileName, $selectedParams")

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_LANDSCAPE, isLandscape).apply()
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
        }

        chart = findViewById(R.id.chartFull)
        findViewById<TextView>(R.id.tvTitle).text = "Графики Данных"
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
            }
            axisRight.isEnabled = false

            setNoDataText("Нет данных для отображения")
        }
    }

    private fun drawChart() {
        val csvInfo = CsvDataParser.parseCsvFile(filePath)
        if (csvInfo == null || selectedParams.isEmpty()) {
            return
        }

        if (fromTimestamp > 0 && toTimestamp > 0 && fromTimestamp > toTimestamp) {
            return
        }

        val dataSets = mutableListOf<LineDataSet>()

        for ((index, param) in selectedParams.withIndex()) {
            val entries = mutableListOf<Entry>()
            val breakPoints = mutableListOf<Entry>()
            var prevValue: Float? = null
            var lastEntry: Entry? = null

            for (point in csvInfo.dataPoints) {
                val ts = point.timestamp
                if (fromTimestamp > 0 && ts < fromTimestamp) continue
                if (toTimestamp > 0 && ts > toTimestamp) continue
                point.values[param]?.let { value ->
                    val entry = Entry(ts.toFloat(), value.toFloat())
                    entries.add(entry)
                    lastEntry = entry

                    val currVal = value.toFloat()
                    if (prevValue == null || currVal != prevValue) {
                        breakPoints.add(entry)
                    }
                    prevValue = currVal
                }
            }

            if (lastEntry != null && !breakPoints.any { it.x == lastEntry.x && it.y == lastEntry.y }) {
                breakPoints.add(lastEntry)
            }

            if (entries.isNotEmpty()) {
                val color = CHART_COLORS[index % CHART_COLORS.size]
                val dataSet = LineDataSet(breakPoints, param).apply {
                    this.color = color
                    lineWidth = 2.5f
                    setDrawCircles(true)
                    circleRadius = 3.5f
                    setDrawCircleHole(true)
                    circleHoleRadius = 1.5f
                    circleColors = listOf(color)
                    setDrawValues(true)
                    valueTextColor = color
                    valueTextSize = 9f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return if (value == value.toLong().toFloat()) {
                                value.toLong().toString()
                            } else {
                                String.format("%.1f", value)
                            }
                        }
                    }
                    mode = LineDataSet.Mode.LINEAR
                }
                dataSets.add(dataSet)
            }
        }

        if (dataSets.isEmpty()) {
            return
        }

        chart.data = LineData(dataSets.toList())

        chart.axisLeft.removeAllLimitLines()

        for ((index, param) in selectedParams.withIndex()) {
            val bounds = paramBounds[param] ?: continue
            val (lower, upper) = bounds
            val color = CHART_COLORS[index % CHART_COLORS.size]

            lower?.let {
                val lowerLine = LimitLine(it.toFloat(), "мин $param").apply {
                    lineColor = color
                    lineWidth = 1.5f
                    enableDashedLine(8f, 4f, 0f)
                    textColor = color
                    textSize = 10f
                }
                chart.axisLeft.addLimitLine(lowerLine)
            }

            upper?.let {
                val upperLine = LimitLine(it.toFloat(), "макс $param").apply {
                    lineColor = color
                    lineWidth = 1.5f
                    enableDashedLine(8f, 4f, 0f)
                    textColor = color
                    textSize = 10f
                }
                chart.axisLeft.addLimitLine(upperLine)
            }
        }

        val xMin = dataSets.first().xMin
        val xMax = dataSets.first().xMax
        val xRange = xMax - xMin

        chart.invalidate()

        chart.post {
            chart.setVisibleXRangeMaximum(xRange)
            chart.setVisibleXRangeMinimum(xRange / 1000)
            chart.moveViewToX(xMin)
        }

        LogStorageManager.logMessage("График построен")
    }

    private fun exportToPdf() {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(chart.width, chart.height, 1).create()
            val page = pdfDocument.startPage(pageInfo)

            val canvas = page.canvas
            chart.draw(canvas)

            pdfDocument.finishPage(page)

            val fileName = "graph_${System.currentTimeMillis()}.pdf"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val mpumFolder = File(downloadsDir, "MPUMUMPOS Downloads")
            if (!mpumFolder.exists()) mpumFolder.mkdirs()
            val file = File(mpumFolder, fileName)

            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }

            pdfDocument.close()

            Toast.makeText(this, "Сохранено: ${file.absolutePath}", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
