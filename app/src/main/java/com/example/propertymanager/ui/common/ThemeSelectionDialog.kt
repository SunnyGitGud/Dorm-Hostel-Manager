package com.example.propertymanager.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource // ADDED
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
// import androidx.compose.foundation.layout.height // Unused, can be removed
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple // UNCOMMENTED
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember // ADDED
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.propertymanager.ui.theme.AppTheme
import com.example.propertymanager.ui.theme.dialogThemes
import com.example.propertymanager.ui.viewmodel.ThemeViewModel

@Composable
fun ThemeSelectionDialog(
    themeViewModel: ThemeViewModel,
    onDismissRequest: () -> Unit
) {
    val currentSelectedTheme by themeViewModel.selectedTheme.collectAsState()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select Theme") },
        text = {
            Column {
                dialogThemes.forEach { themeEntry ->
                    val isCurrentlySelectedBase = currentSelectedTheme.getBaseForDialog() == themeEntry
                    val interactionSource = remember { MutableInteractionSource() } // ADDED

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClick = {
                                    themeViewModel.setTheme(themeEntry)
                                    onDismissRequest()
                                },
                                interactionSource = interactionSource, // ADDED
                                indication = ripple(bounded = true) // UNCOMMENTED
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isCurrentlySelectedBase,
                            onClick = { // This onClick for RadioButton is now redundant if the whole row is clickable
                                themeViewModel.setTheme(themeEntry)
                                onDismissRequest()
                            }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = themeEntry.displayName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
