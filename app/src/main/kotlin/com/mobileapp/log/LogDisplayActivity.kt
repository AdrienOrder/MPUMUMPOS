package com.mobileapp.log

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mobileapp.main.MainActivity
import com.mobileapp.storage.StorageActivity

class LogDisplayActivity : AppCompatActivity() {

    companion object {
        private var instance: LogDisplayActivity? = null

        fun updateLogDisplay() {
            instance?.runOnUiThread {
                instance?.updateLogDisplayInternal()
            }
        }
    }

    private lateinit var tvLog: TextView

    private fun centerDialogButtons(dialog: AlertDialog) {
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            positiveButton.setTextColor(ContextCompat.getColor(this, R.color.blue_500))
            negativeButton.setTextColor(ContextCompat.getColor(this, R.color.blue_500))

            val buttonPanel = positiveButton.parent as? LinearLayout
            buttonPanel?.gravity = android.view.Gravity.CENTER
            buttonPanel?.layoutParams?.width = LinearLayout.LayoutParams.MATCH_PARENT
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        instance = this
        tvLog = findViewById(R.id.tvLog)

        findViewById<Button>(R.id.btnClear)?.setOnClickListener {
            val dialog = AlertDialog.Builder(this)
                .setView(createClearLogDialogView())
                .setPositiveButton(getString(R.string.dialog_button_confirm)) { _, _ ->
                    LogStorageManager.clearLogs()
                    tvLog.text = ""
                    Toast.makeText(this, R.string.toast_logs_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(getString(R.string.dialog_button_cancel), null)
                .create()

            centerDialogButtons(dialog)
            dialog.show()
        }

        findViewById<Button>(R.id.btnCopy)?.setOnClickListener {
            copyToClipboard()
        }

        findViewById<ImageButton>(R.id.btnLogs).setOnClickListener {
        }

        findViewById<ImageButton>(R.id.btnHome)?.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }

        findViewById<ImageButton>(R.id.btnFiles)?.setOnClickListener {
            startActivity(Intent(this, StorageActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
    }

    override fun onResume() {
        super.onResume()
        updateLogDisplay()
    }

    private fun updateLogDisplayInternal() {
        tvLog.text = LogStorageManager.getLogs()
    }

    private fun createClearLogDialogView(): LinearLayout {
        val titleView = TextView(this).apply {
            text = getString(R.string.dialog_title_clear_logs)
            setTextColor(ContextCompat.getColor(context, R.color.blue_500))
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 40, 0, 20)
        }

        val messageView = TextView(this).apply {
            text = getString(R.string.dialog_message_clear_logs)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(titleView)
        container.addView(messageView)
        return container
    }

    private fun copyToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Лог операций", LogStorageManager.getLogs())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Лог скопирован в буфер обмена", Toast.LENGTH_SHORT).show()
    }
}
