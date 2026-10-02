package com.sortfold.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sortfold.app.R
import com.sortfold.app.ui.common.SortedBarsLoader

private data class OnboardingPage(val titleRes: Int, val bodyRes: Int)

/** First launch walkthrough: 3 pages, skippable, state survives rotation. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = listOf(
        OnboardingPage(R.string.onboard1_title, R.string.onboard1_body),
        OnboardingPage(R.string.onboard2_title, R.string.onboard2_body),
        OnboardingPage(R.string.onboard3_title, R.string.onboard3_body),
    )
    var page by rememberSaveable { mutableIntStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(
            onClick = onDone,
            modifier = Modifier.align(Alignment.End),
        ) { Text(stringResource(R.string.action_skip)) }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (page == 0) {
                Box(Modifier.padding(bottom = 8.dp)) {
                    SortedBarsLoader(label = stringResource(R.string.onboard_loader_label))
                }
            }
            Text(
                stringResource(pages[page].titleRes),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(pages[page].bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(pages.size) { i ->
                    Surface(
                        shape = CircleShape,
                        color = if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Spacer(Modifier.size(8.dp))
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (page > 0) {
                OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_back))
                }
            }
            Button(
                onClick = { if (page < pages.size - 1) page++ else onDone() },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(
                        if (page < pages.size - 1) R.string.action_next else R.string.onboard_start,
                    ),
                )
            }
        }
    }
}
