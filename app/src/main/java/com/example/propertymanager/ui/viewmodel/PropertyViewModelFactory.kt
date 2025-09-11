package com.example.propertymanager.ui.viewmodel

import android.app.Application // Import Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.propertymanager.data.repository.PropertyRepository
import com.example.propertymanager.data.repository.RoomRepository
import com.example.propertymanager.data.repository.MonthlyBillRepository
import com.example.propertymanager.data.repository.PaymentInstallmentRepository
import com.example.propertymanager.data.repository.TenantRepository
import com.example.propertymanager.services.DataImportExportService

class PropertyViewModelFactory(
    private val application: Application, // Added Application
    private val propertyRepository: PropertyRepository,
    private val roomRepository: RoomRepository, 
    private val monthlyBillRepository: MonthlyBillRepository, 
    private val paymentInstallmentRepository: PaymentInstallmentRepository,
    private val tenantRepository: TenantRepository
) : ViewModelProvider.Factory {

    // Create the service instance here, as the factory has all dependencies
    private val dataImportExportService = DataImportExportService(
        propertyRepository,
        roomRepository,
        monthlyBillRepository,
        paymentInstallmentRepository,
        tenantRepository
    )

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PropertyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PropertyViewModel(
                application, // Pass Application instance
                propertyRepository,
                roomRepository,
                monthlyBillRepository,
                paymentInstallmentRepository,
                tenantRepository,
                dataImportExportService
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
