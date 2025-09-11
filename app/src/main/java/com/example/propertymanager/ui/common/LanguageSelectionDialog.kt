package com.example.propertymanager.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class LanguageOption(val code: String, val displayName: String)

val availableLanguages = listOf(
    LanguageOption("en", "English"),
    LanguageOption("es", "Español (Spanish)"),
    LanguageOption("hi", "हिन्दी (Hindi)"),
    LanguageOption("fr", "Français (French)")
    // Add more languages here
)

@Composable
fun LanguageSelectionDialog(
    currentLanguageCode: String, // To pre-select the current language
    onLanguageSelected: (LanguageOption) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedOption by remember {
        mutableStateOf(availableLanguages.firstOrNull { it.code == currentLanguageCode } ?: availableLanguages.first())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Language") },
        text = {
            Column {
                Text(
                    "Note: App will restart or refresh to apply language changes. (This feature is currently a demo)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn {
                    items(availableLanguages) { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedOption = lang }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lang.displayName,
                                style = if (selectedOption.code == lang.code) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                color = if (selectedOption.code == lang.code) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onLanguageSelected(selectedOption)
                    onDismiss()
                }
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


