package com.example.propertymanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.propertymanager.data.repository.PropertyRepository
// Removed: import com.example.propertymanager.data.dao.MonthlyBillDao
// Removed: import com.example.propertymanager.data.repository.RoomRepository

class PropertyViewModelFactory(
    private val propertyRepository: PropertyRepository // Reverted to only PropertyRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PropertyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Reverted to pass only PropertyRepository
            return PropertyViewModel(propertyRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
