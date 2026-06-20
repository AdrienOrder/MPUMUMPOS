package com.mobileapp.dialog

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DialogManager(private val context: Context) {

    private fun centerDialogButtons(dialog: AlertDialog) {
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            positiveButton.setTextColor(ContextCompat.getColor(context, R.color.blue_500))
            negativeButton.setTextColor(ContextCompat.getColor(context, R.color.blue_500))

            val buttonPanel = positiveButton.parent as? LinearLayout
            buttonPanel?.gravity = android.view.Gravity.CENTER
            buttonPanel?.layoutParams?.width = LinearLayout.LayoutParams.MATCH_PARENT
        }
    }

    fun showEditConfirmationDialog(
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        val titleView = TextView(context).apply {
            text = context.getString(R.string.dialog_title_confirm_changes)
            setTextColor(ContextCompat.getColor(context, R.color.blue_500))
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 40, 0, 20)
        }

        val messageView = TextView(context).apply {
            text = context.getString(R.string.dialog_message_confirm_changes)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(titleView)
        container.addView(messageView)

        val dialog = AlertDialog.Builder(context)
            .setView(container)
            .setPositiveButton(context.getString(R.string.dialog_button_confirm)) { _, _ ->
                onConfirm()
            }
            .setNegativeButton(context.getString(R.string.dialog_button_cancel)) { _, _ ->
                onCancel()
            }
            .create()

        centerDialogButtons(dialog)
        dialog.show()
    }

    fun showSyncTimeConfirmationDialog(onSync: () -> Unit) {
        val titleView = TextView(context).apply {
            text = context.getString(R.string.dialog_title_sync_time)
            setTextColor(ContextCompat.getColor(context, R.color.blue_500))
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 40, 0, 20)
        }

        val currentTime = SimpleDateFormat("HH:mm dd.MM.yyyy", Locale.getDefault()).format(Date())
        val messageView = TextView(context).apply {
            text = context.getString(R.string.dialog_message_send_time, currentTime)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(titleView)
        container.addView(messageView)

        val dialog = AlertDialog.Builder(context)
            .setView(container)
            .setPositiveButton(context.getString(R.string.dialog_button_confirm)) { _, _ ->
                onSync()
            }
            .setNegativeButton(context.getString(R.string.dialog_button_cancel), null)
            .create()

        centerDialogButtons(dialog)
        dialog.show()
    }

    fun showIntervalPickerDialog(currentValue: Int, onSelected: (Int) -> Unit) {
        val totalMinutes = currentValue
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60

        val pickerDays = NumberPicker(context).apply {
            minValue = 0
            maxValue = 30
            value = days
            wrapSelectorWheel = false
        }

        val pickerHours = NumberPicker(context).apply {
            minValue = 0
            maxValue = 23
            value = hours
            wrapSelectorWheel = false
        }

        val pickerMinutes = NumberPicker(context).apply {
            minValue = 0
            maxValue = 59
            value = minutes
            wrapSelectorWheel = false
        }

        val labelDays = TextView(context).apply {
            text = "ДД"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.blue_500))
        }
        val labelHours = TextView(context).apply {
            text = "ЧЧ"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.blue_500))
        }
        val labelMinutes = TextView(context).apply {
            text = "ММ"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.blue_500))
        }

        val pickersLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        fun addSeparator(char: String) {
            val separator = TextView(context).apply {
                text = char
                textSize = 20f
                gravity = android.view.Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.blue_500))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 40, 0, 0)
                }
            }
            pickersLayout.addView(separator)
        }

        fun addPickerWithLabel(picker: NumberPicker, label: TextView) {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            container.addView(label)
            container.addView(picker)
            pickersLayout.addView(container)
        }

        addPickerWithLabel(pickerDays, labelDays)
        addSeparator(":")
        addPickerWithLabel(pickerHours, labelHours)
        addSeparator(":")
        addPickerWithLabel(pickerMinutes, labelMinutes)

        val titleView = TextView(context).apply {
            text = "Выберите интервал"
            setTextColor(ContextCompat.getColor(context, R.color.blue_500))
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 40, 0, 20)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(titleView)
        container.addView(pickersLayout)

        val dialog = AlertDialog.Builder(context)
            .setView(container)
            .setPositiveButton(context.getString(R.string.dialog_button_confirm)) { _, _ ->
                val totalMinutesSelected = pickerDays.value * 24 * 60 +
                    pickerHours.value * 60 +
                    pickerMinutes.value
                onSelected(totalMinutesSelected)
            }
            .setNegativeButton(context.getString(R.string.dialog_button_cancel), null)
            .create()

        centerDialogButtons(dialog)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        dialog.show()
    }

    fun showStartTimePickerDialog(onSelected: (String) -> Unit) {
        val now = Calendar.getInstance()
        val initialHour = now.get(Calendar.HOUR_OF_DAY)
        val initialMinute = now.get(Calendar.MINUTE)
        val initialDay = now.get(Calendar.DAY_OF_MONTH)
        val initialMonth = now.get(Calendar.MONTH) + 1
        val initialYear = now.get(Calendar.YEAR)

        fun getMaxDays(month: Int, year: Int): Int {
            return when (month) {
                2 -> if (isLeapYear(year)) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }
        }

        val pickerHour = NumberPicker(context).apply {
            minValue = 0
            maxValue = 23
            value = initialHour
            wrapSelectorWheel = false
        }

        val pickerMinute = NumberPicker(context).apply {
            minValue = 0
            maxValue = 59
            value = initialMinute
            wrapSelectorWheel = false
        }

        val pickerDay = NumberPicker(context).apply {
            minValue = 1
            maxValue = getMaxDays(initialMonth, initialYear)
            value = initialDay
            wrapSelectorWheel = false
        }

        val pickerMonth = NumberPicker(context).apply {
            minValue = 1
            maxValue = 12
            value = initialMonth
            wrapSelectorWheel = false
        }

        val pickerYear = NumberPicker(context).apply {
            minValue = 1900
            maxValue = 2199
            value = initialYear
            wrapSelectorWheel = false
        }

        val labelHour = TextView(context).apply {
            text = "ЧЧ"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.blue_500))
        }
        val labelMinute = TextView(context).apply {
            text = "ММ"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.blue_500))
        }
        val labelDay = TextView(context).apply {
            text = "ДД"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.blue_500))
        }
        val labelMonth = TextView(context).apply {
            text = "ММ"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.blue_500))
        }
        val labelYear = TextView(context).apply {
            text = "ГГГГ"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.blue_500))
        }

        val pickersLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        fun addSeparator(char: String) {
            val separator = TextView(context).apply {
                text = char
                textSize = 20f
                gravity = android.view.Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.blue_500))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 40, 0, 0)
                }
            }
            pickersLayout.addView(separator)
        }

        fun addPickerWithLabel(picker: NumberPicker, label: TextView) {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            container.addView(label)
            container.addView(picker)
            pickersLayout.addView(container)
        }

        addPickerWithLabel(pickerHour, labelHour)
        addSeparator(":")
        addPickerWithLabel(pickerMinute, labelMinute)
        addSeparator("|")
        addPickerWithLabel(pickerDay, labelDay)
        addSeparator(".")
        addPickerWithLabel(pickerMonth, labelMonth)
        addSeparator(".")
        addPickerWithLabel(pickerYear, labelYear)

        pickerMonth.setOnValueChangedListener { _, _, newMonth ->
            val maxDays = getMaxDays(newMonth, pickerYear.value)
            pickerDay.maxValue = maxDays
            if (pickerDay.value > maxDays) {
                pickerDay.value = maxDays
            }
        }

        pickerYear.setOnValueChangedListener { _, _, newYear ->
            val maxDays = getMaxDays(pickerMonth.value, newYear)
            pickerDay.maxValue = maxDays
            if (pickerDay.value > maxDays) {
                pickerDay.value = maxDays
            }
        }

        val titleView = TextView(context).apply {
            text = context.getString(R.string.dialog_title_select_time)
            setTextColor(ContextCompat.getColor(context, R.color.blue_500))
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 40, 0, 20)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(titleView)
        container.addView(pickersLayout)

        val dialog = AlertDialog.Builder(context)
            .setView(container)
            .setPositiveButton(context.getString(R.string.dialog_button_confirm)) { _, _ ->
                val selectedTime = String.format("%02d:%02d %02d.%02d.%04d",
                    pickerHour.value,
                    pickerMinute.value,
                    pickerDay.value,
                    pickerMonth.value,
                    pickerYear.value)
                onSelected(selectedTime)
            }
            .setNegativeButton(context.getString(R.string.dialog_button_cancel), null)
            .create()

        centerDialogButtons(dialog)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        dialog.show()
    }

    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    fun showPermissionsRequiredDialog(onOpenSettings: () -> Unit) {
        val titleView = TextView(context).apply {
            text = "Требуются разрешения"
            setTextColor(ContextCompat.getColor(context, R.color.blue_500))
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 40, 0, 20)
        }

        val messageView = TextView(context).apply {
            text = "Для подключения\nк Bluetooth-устройству\n приложению нужны разрешения:\n\n Доступ к Bluetooth\n Доступ к местоположению\n\nНажмите «НАСТРОЙКИ», включите эти разрешения, затем вернитесь в приложение."
            setTextColor(ContextCompat.getColor(context, R.color.white))
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(titleView)
        container.addView(messageView)

        val dialog = AlertDialog.Builder(context)
            .setView(container)
            .setPositiveButton("Настройки") { _, _ ->
                onOpenSettings()
            }
            .setNegativeButton("Отмена", null)
            .create()

        centerDialogButtons(dialog)
        dialog.show()
    }
}
