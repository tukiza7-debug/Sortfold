package com.sortfold.app

import android.app.Application
import androidx.work.Configuration
import com.sortfold.app.data.db.SortfoldDatabase
import com.sortfold.app.data.prefs.SettingsRepository
import com.sortfold.app.data.repo.UpdateRepository
import com.sortfold.app.error.CrashHandler
import com.sortfold.app.error.ErrorExporter
import com.sortfold.app.error.ErrorRepository
import com.sortfold.app.core.history.UndoManager
import com.sortfold.app.core.mover.Mover
import com.sortfold.app.core.scanner.MediaScanner
import com.sortfold.app.work.ProgressNotifications
import com.sortfold.app.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SortfoldApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CrashHandler.install(this)
        ProgressNotifications.ensureChannels(this)
        WorkScheduler.scheduleAll(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.WARN)
            .build()
}

/** Manual dependency container: small, explicit, easy to audit. */
class AppContainer(val appContext: android.content.Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: SortfoldDatabase by lazy { SortfoldDatabase.build(appContext) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val errorRepository: ErrorRepository by lazy { ErrorRepository(appContext, database) }
    val updateRepository: UpdateRepository by lazy { UpdateRepository(appContext) }
    val scanner: MediaScanner by lazy { MediaScanner(appContext) }
    val mover: Mover by lazy { Mover(appContext) }
    val undoManager: UndoManager by lazy { UndoManager(appContext, database) }
    val errorExporter: ErrorExporter by lazy { ErrorExporter(appContext) }
}
