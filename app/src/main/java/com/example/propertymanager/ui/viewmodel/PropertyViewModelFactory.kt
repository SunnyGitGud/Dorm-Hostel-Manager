package com.example.propertymanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.propertymanager.data.repository.PropertyRepository
import com.example.propertymanager.data.repository.RoomRepository
import com.example.propertymanager.data.repository.MonthlyBillRepository
import com.example.propertymanager.data.repository.PaymentInstallmentRepository
import com.example.propertymanager.data.repository.TenantRepository // Added import

class PropertyViewModelFactory(
    private val propertyRepository: PropertyRepository,
    private val roomRepository: RoomRepository, 
    private val monthlyBillRepository: MonthlyBillRepository, 
    private val paymentInstallmentRepository: PaymentInstallmentRepository,
    private val tenantRepository: TenantRepository // Added
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PropertyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PropertyViewModel(
                propertyRepository,
                roomRepository,
                monthlyBillRepository,
                paymentInstallmentRepository,
                tenantRepository // Added
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
