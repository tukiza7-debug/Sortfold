package com.sortfold.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortfold.app.AppContainer
import com.sortfold.app.R
import com.sortfold.app.util.Locales
import kotlinx.coroutines.launch

/**
 * Language screen: English, Bahasa Melayu, Bahasa Indonesia, Arabic (full
 * RTL), Simplified Chinese, plus the system default. Applying the locale is
 * instant and recreation-safe (AppCompatDelegate handles persistence on
 * Android 12 and below; the system per-app language on 13+).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(container: AppContainer, onBack: () -> Unit) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = com.sortfold.app.data.prefs.AppSettings(),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_language)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.language_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Locales.available.forEach { locale ->
                Card {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = settings.languageTag == locale.tag,
                                onClick = {
                                    Locales.apply(locale.tag)
                                    container.appScope.launch {
                                        container.settingsRepository.setLanguageTag(locale.tag)
                                    }
                                },
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = settings.languageTag == locale.tag,
                            onClick = null, // handled by the row
                        )
                        Column {
                            Text(
                                if (locale.tag == null) stringResource(R.string.lang_system) else locale.nativeName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                if (locale.tag == null) stringResource(R.string.lang_system_desc) else locale.englishName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
