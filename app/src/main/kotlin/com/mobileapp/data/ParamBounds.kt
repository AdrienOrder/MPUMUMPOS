package com.mobileapp.data

data class ParamBounds(
    val name: String,
    var isSelected: Boolean = false,
    var lowerBound: Double? = null,
    var upperBound: Double? = null
)
