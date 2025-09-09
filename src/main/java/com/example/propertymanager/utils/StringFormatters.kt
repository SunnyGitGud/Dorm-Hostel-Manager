package com.example.propertymanager.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// formatDate helper function
fun formatDate(timestamp: Long?): String {
    if (timestamp == null || timestamp == 0L) return "N/A"
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}