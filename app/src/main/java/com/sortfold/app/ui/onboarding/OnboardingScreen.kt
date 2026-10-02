package com.sortfold.app.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0) { pages.size }
    val page = pagerState.currentPage

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

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { index ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (index == 0) {
                    Box(Modifier.padding(bottom = 8.dp)) {
                        SortedBarsLoader(label = stringResource(R.string.onboard_loader_label))
                    }
                }
                Text(
                    stringResource(pages[index].titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(pages[index].bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Animated page indicator: the active dot grows with a spring.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            repeat(pages.size) { i ->
                val size by animateDpAsState(
                    targetValue = if (i == pagerState.currentPage) 12.dp else 8.dp,
                    animationSpec = spring(dampingRatio = 0.6f),
                    label = "dot-$i",
                )
                Surface(
                    shape = CircleShape,
                    color = if (i == pagerState.currentPage) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Spacer(Modifier.size(size))
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (page > 0) {
                OutlinedButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(page - 1) } },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_back))
                }
            }
            Button(
                onClick = {
                    if (page < pages.size - 1) scope.launch { pagerState.animateScrollToPage(page + 1) } else onDone()
                },
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
