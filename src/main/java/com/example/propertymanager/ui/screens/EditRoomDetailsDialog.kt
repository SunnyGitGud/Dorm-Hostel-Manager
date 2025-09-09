package com.example.propertymanager.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.propertymanager.data.entities.RoomEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRoomDetailsDialog(
    room: RoomEntity,
    onDismiss: () -> Unit,
    onSave: (updatedRoom: RoomEntity) -> Unit
) {
    var name by remember(room.name) { mutableStateOf(room.name) }
    var rentString by remember(room.rent) { mutableStateOf(room.rent.toString()) }
    var electricityRateString by remember(room.electricityRatePerUnit) { mutableStateOf(room.electricityRatePerUnit.toString()) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var rentError by remember { mutableStateOf<String?>(null) }
    var electricityRateError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Room Details") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text("Room Name/Number") },
                    isError = nameError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (nameError != null) {
                    Text(nameError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = rentString,
                    onValueChange = { rentString = it; rentError = null },
                    label = { Text("Monthly Rent (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = rentError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (rentError != null) {
                    Text(rentError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = electricityRateString,
                    onValueChange = { electricityRateString = it; electricityRateError = null },
                    label = { Text("Electricity Rate per Unit (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = electricityRateError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (electricityRateError != null) {
                    Text(electricityRateError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    var valid = true
                    if (name.isBlank()) {
                        nameError = "Name cannot be empty"
                        valid = false
                    }

                    val rentDouble = rentString.toDoubleOrNull()
                    if (rentString.isBlank()) {
                        rentError = "Rent cannot be empty"
                        valid = false
                    } else if (rentDouble == null || rentDouble <= 0) {
                        rentError = "Please enter a valid positive rent amount"
                        valid = false
                    }

                    val electricityRateDouble = electricityRateString.toDoubleOrNull()
                    if (electricityRateString.isBlank()) {
                        electricityRateError = "Rate cannot be empty"
                        valid = false
                    } else if (electricityRateDouble == null || electricityRateDouble < 0) {
                        electricityRateError = "Please enter a valid rate (e.g., 0 or 10.0)"
                        valid = false
                    }

                    if (valid) {
                        val updatedRoom = room.copy(
                            name = name,
                            rent = rentDouble!!,
                            electricityRatePerUnit = electricityRateDouble!!
                        )
                        onSave(updatedRoom)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}