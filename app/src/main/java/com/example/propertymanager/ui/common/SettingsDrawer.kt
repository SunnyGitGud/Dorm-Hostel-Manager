package com.example.propertymanager.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.Language // Icon for Change Language
import androidx.compose.material.icons.filled.NightsStay
// import androidx.compose.material.icons.filled.Settings // Already present, can be removed if not used directly here
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
// Removed duplicate import of androidx.compose.material3.Text

// Enum to represent different settings options
enum class SettingsAction {
    EXPORT_DATA,
    IMPORT_DATA,
    SYNC_GOOGLE_DRIVE,
    TOGGLE_DARK_MODE,
    CHANGE_LANGUAGE // New action for changing language
    // Add other settings actions here
}

@Composable
fun SettingsDrawerContent(
    modifier: Modifier = Modifier,
    onItemSelected: (SettingsAction) -> Unit
) {
    ModalDrawerSheet(modifier = modifier) {
        Column(modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            SettingsDrawerItem(
                label = "Export Data",
                icon = Icons.Filled.CloudUpload,
                onClick = { onItemSelected(SettingsAction.EXPORT_DATA) }
            )
            SettingsDrawerItem(
                label = "Import Data",
                icon = Icons.Filled.CloudDownload,
                onClick = { onItemSelected(SettingsAction.IMPORT_DATA) }
            )
            SettingsDrawerItem(
                label = "Sync with Google Drive",
                icon = Icons.Filled.DriveFolderUpload,
                onClick = { onItemSelected(SettingsAction.SYNC_GOOGLE_DRIVE) },
                comingSoon = true
            )
            SettingsDrawerItem(
                label = "Toggle Dark Mode",
                icon = Icons.Filled.NightsStay,
                onClick = { onItemSelected(SettingsAction.TOGGLE_DARK_MODE) },
                comingSoon = true
            )
            SettingsDrawerItem(
                label = "Change Language",
                icon = Icons.Filled.Language, // New Icon
                onClick = { onItemSelected(SettingsAction.CHANGE_LANGUAGE) },
                comingSoon = true // Initially mark as coming soon
            )
            // Add more items as needed
        }
    }
}

@Composable
private fun SettingsDrawerItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    comingSoon: Boolean = false
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = label) },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                if (comingSoon) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "(Coming Soon)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        },
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
