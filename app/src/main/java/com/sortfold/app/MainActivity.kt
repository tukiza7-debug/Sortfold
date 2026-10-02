package com.sortfold.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortfold.app.data.prefs.AppSettings
import com.sortfold.app.ui.SortfoldRoot
import com.sortfold.app.ui.theme.SortfoldTheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single activity. All UI is Compose; navigation, wizard state and jobs live
 * in ViewModels and Room so rotation, split-screen and process death never
 * lose state or interrupt a running job.
 */
class MainActivity : AppCompatActivity() {

    /** Set when the app is opened from a completion / update notification. */
    val pendingJobId = MutableStateFlow<Long?>(null)

    var windowSizeClass: WindowSizeClass? = null
        private set

    private var keepSplash = mutableStateOf(true)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { keepSplash.value }
        enableEdgeToEdge()

        val container = (application as SortfoldApp).container
        // Warm up the database off the UI thread; splash holds under a second.
        Thread {
            runCatching { container.database.openHelper.readableDatabase }
            keepSplash.value = false
        }.start()

        handleIntent(intent)
        setContentWithSizeClass()
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    private fun setContentWithSizeClass() {
        setContent {
            val settings by (application as SortfoldApp).container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            val wsc = calculateWindowSizeClass(this)
            windowSizeClass = wsc
            val reducedMotion = settings.reduceAnimations ||
                android.provider.Settings.Global.getFloat(
                    contentResolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
                ) == 0f
            SortfoldTheme(settings.themeMode, settings.dynamicColor, reducedMotion) {
                SortfoldRoot(
                    container = (application as SortfoldApp).container,
                    activity = this,
                    windowSizeClass = wsc,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val jobId = intent?.getLongExtra(com.sortfold.app.work.ProgressNotifications.EXTRA_JOB_ID, -1L)
        if (jobId != null && jobId > 0) pendingJobId.value = jobId
    }
}
