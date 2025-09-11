package com.example.propertymanager.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource // ADDED
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.ripple.rememberRipple // CHANGED
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
// import androidx.compose.material3.LocalIndication // COMMENTED OUT FOR DEBUGGING
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.RadioButton // ADDED
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
// import androidx.compose.runtime.CompositionLocalProvider // COMMENTED OUT FOR DEBUGGING
import androidx.compose.runtime.collectAsState // ADDED
import androidx.compose.runtime.getValue // ADDED
import androidx.compose.runtime.remember // ADDED
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection // ADDED
import androidx.compose.ui.unit.dp
import com.example.propertymanager.ui.theme.AppTheme // ADDED
import com.example.propertymanager.ui.viewmodel.ThemeViewModel // ADDED

// Enum to represent different settings options
enum class SettingsAction {
    EXPORT_DATA,
    IMPORT_DATA,
    SYNC_GOOGLE_DRIVE,
    TOGGLE_DARK_MODE,
    CHANGE_LANGUAGE
}

@Composable
fun SettingsDrawerContent(
    modifier: Modifier = Modifier,
    themeViewModel: ThemeViewModel, // ADDED ThemeViewModel
    onItemSelected: (SettingsAction) -> Unit
) {
    val currentTheme by themeViewModel.selectedTheme.collectAsState() // ADDED

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
                icon = Icons.Filled.Language,
                onClick = { onItemSelected(SettingsAction.CHANGE_LANGUAGE) },
                comingSoon = true
            )

            // ADDED: Theme Selection Section
            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            Text(
                text = "Select Theme",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
            )
            AppTheme.entries.toTypedArray().forEach { theme ->
                ThemeSelectionRow(
                    theme = theme,
                    isSelected = theme == currentTheme,
                    onThemeSelected = { themeViewModel.setTheme(it) }
                )
            }
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

// ADDED: ThemeSelectionRow composable with ripple fix
@Composable
private fun ThemeSelectionRow(
    theme: AppTheme,
    isSelected: Boolean,
    onThemeSelected: (AppTheme) -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    val interactionSource = remember { MutableInteractionSource() }

    // Provide M3 ripple for this specific clickable area
    // CompositionLocalProvider(LocalIndication provides rememberRipple(bounded = true)) { // COMMENTED OUT FOR DEBUGGING
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = { onThemeSelected(theme) },
                    interactionSource = interactionSource,
                    indication = null // COMMENTED OUT FOR DEBUGGING: rememberRipple(bounded = true)
                )
                .padding(
                    start = NavigationDrawerItemDefaults.ItemPadding.calculateLeftPadding(layoutDirection) + 8.dp,
                    end = NavigationDrawerItemDefaults.ItemPadding.calculateRightPadding(layoutDirection) + 8.dp,
                    top = 12.dp,
                    bottom = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = { onThemeSelected(theme) }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = theme.displayName, style = MaterialTheme.typography.bodyLarge)
        }
    // } // COMMENTED OUT FOR DEBUGGING
}
