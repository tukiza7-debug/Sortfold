package com.sortfold.app.ui.errors

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.sortfold.app.AppContainer
import com.sortfold.app.R
import com.sortfold.app.ui.common.DestructiveConfirmDialog
import com.sortfold.app.ui.common.Formatters
import com.sortfold.app.ui.common.KeyValueRow
import kotlinx.coroutines.launch

/** Detail view of one error entry: copy, delete, full stack trace. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorDetailScreen(container: AppContainer, errorId: Long, onBack: () -> Unit) {
    var error by remember { mutableStateOf<com.sortfold.app.data.db.ErrorEntity?>(null) }
    androidx.compose.runtime.LaunchedEffect(errorId) {
        error = container.database.errorDao().byId(errorId)
    }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.error_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            error?.let { e ->
                                clipboard.setText(
                                    AnnotatedString("${e.type}: ${e.message}\n${e.stackTrace.orEmpty()}"),
                                )
                                android.widget.Toast.makeText(
                                    context, R.string.error_copied, android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.action_copy))
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        val e = error
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (e == null) {
                Text(stringResource(R.string.job_not_found))
                return@Column
            }
            Text(e.type, style = MaterialTheme.typography.headlineSmall)
            Text(e.message, style = MaterialTheme.typography.bodyLarge)
            KeyValueRow(stringResource(R.string.error_module), e.module)
            KeyValueRow(stringResource(R.string.error_severity), severityLabel(e.severity))
            KeyValueRow(stringResource(R.string.error_time), Formatters.date(e.timestamp))
            KeyValueRow(stringResource(R.string.error_device), "${e.deviceModel}, Android ${e.androidVersion}")
            KeyValueRow(stringResource(R.string.error_app_version), e.appVersion)
            e.jobId?.let { KeyValueRow(stringResource(R.string.error_job), "#$it") }
            e.stackTrace?.let { trace ->
                Text(stringResource(R.string.error_stack), style = MaterialTheme.typography.titleSmall)
                Text(
                    trace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }

        if (confirmDelete && error != null) {
            DestructiveConfirmDialog(
                title = stringResource(R.string.action_delete),
                body = stringResource(R.string.error_delete_body),
                confirmText = stringResource(R.string.action_delete),
                onConfirm = {
                    container.appScope.launch { container.database.errorDao().delete(error!!.id) }
                },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}
