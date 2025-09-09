package com.example.propertymanager.ui.screens

import androidx.compose.foundation.layout.padding // Added import
import androidx.compose.material3.ExperimentalMaterial3Api // Added import
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier // Added import

@OptIn(ExperimentalMaterial3Api::class) // Added annotation
@Composable
fun PaymentScreen(roomId: Int) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Payments") })
        }
    ) { innerPadding -> // Renamed to innerPadding
        Text(
            text = "Payments for room $roomId",
            modifier = Modifier.padding(innerPadding) // Used innerPadding and Modifier
        )
    }
}
