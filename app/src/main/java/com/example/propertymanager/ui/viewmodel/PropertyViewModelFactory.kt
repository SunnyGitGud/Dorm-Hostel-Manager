package com.example.propertymanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.propertymanager.data.repository.PropertyRepository
import com.example.propertymanager.data.repository.RoomRepository // Added import
import com.example.propertymanager.data.repository.MonthlyBillRepository // Added import
import com.example.propertymanager.data.repository.PaymentInstallmentRepository // Added import

class PropertyViewModelFactory(
    private val propertyRepository: PropertyRepository,
    private val roomRepository: RoomRepository, // Added
    private val monthlyBillRepository: MonthlyBillRepository, // Added
    private val paymentInstallmentRepository: PaymentInstallmentRepository // Added
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PropertyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PropertyViewModel(
                propertyRepository,
                roomRepository,
                monthlyBillRepository,
                paymentInstallmentRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
