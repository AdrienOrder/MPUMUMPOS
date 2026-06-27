package com.mobileapp.visualization

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mobileapp.R

data class ParamWithBounds(
    val name: String,
    var isSelected: Boolean = false,
    var lowerBound: Double? = null,
    var upperBound: Double? = null
)

class ParamAdapter(
    private val onParamChanged: (String, Boolean, Double?, Double?) -> Unit
) : ListAdapter<ParamWithBounds, ParamAdapter.ViewHolder>(ParamDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_param_with_bounds, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.cbParam)
        private val etLowerBound: EditText = itemView.findViewById(R.id.etLowerBound)
        private val etUpperBound: EditText = itemView.findViewById(R.id.etUpperBound)

        fun bind(param: ParamWithBounds) {
            checkBox.text = param.name

            etLowerBound.isEnabled = param.isSelected
            etUpperBound.isEnabled = param.isSelected

            if (!param.isSelected) {
                etLowerBound.setText("")
                etUpperBound.setText("")
            } else {
                etLowerBound.setText(param.lowerBound?.toString() ?: "")
                etUpperBound.setText(param.upperBound?.toString() ?: "")
            }

            checkBox.setOnCheckedChangeListener(null)
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                etLowerBound.isEnabled = isChecked
                etUpperBound.isEnabled = isChecked
                if (!isChecked) {
                    etLowerBound.setText("")
                    etUpperBound.setText("")
                }
                val lower = etLowerBound.text.toString().toDoubleOrNull()
                val upper = etUpperBound.text.toString().toDoubleOrNull()
                onParamChanged(param.name, isChecked, lower, upper)
            }

            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (checkBox.isChecked) {
                        val lower = etLowerBound.text.toString().toDoubleOrNull()
                        val upper = etUpperBound.text.toString().toDoubleOrNull()
                        onParamChanged(param.name, checkBox.isChecked, lower, upper)
                    }
                }
            }

            etLowerBound.addTextChangedListener(textWatcher)
            etUpperBound.addTextChangedListener(textWatcher)
        }
    }

    class ParamDiffCallback : DiffUtil.ItemCallback<ParamWithBounds>() {
        override fun areItemsTheSame(oldItem: ParamWithBounds, newItem: ParamWithBounds) = oldItem.name == newItem.name
        override fun areContentsTheSame(oldItem: ParamWithBounds, newItem: ParamWithBounds) =
            oldItem.name == newItem.name && oldItem.isSelected == newItem.isSelected &&
            oldItem.lowerBound == newItem.lowerBound && oldItem.upperBound == newItem.upperBound
    }
}
