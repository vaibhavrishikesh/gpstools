package com.gpstools.camera.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gpstools.camera.R
import com.gpstools.camera.locale.AppLanguage
import com.gpstools.camera.locale.LocaleStore
import com.gpstools.camera.locale.findActivity
import com.gpstools.camera.ui.navigation.Destination

/**
 * Settings screen. Currently exposes the in-app language toggle (US-013):
 * picking a language persists it and recreates the activity so the new locale
 * applies immediately; "System default" follows the device locale.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(LocaleStore.load(context)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Destination.Settings.labelRes),
            style = MaterialTheme.typography.headlineSmall,
        )

        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.settings_language_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    LanguageRow(
                        label = stringResource(language.labelRes),
                        selected = language == selected,
                        onSelect = {
                            if (language != selected) {
                                selected = language
                                LocaleStore.save(context, language)
                                // Recreate so attachBaseContext re-wraps with the
                                // new locale and the whole UI re-resolves strings.
                                context.findActivity()?.recreate()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
