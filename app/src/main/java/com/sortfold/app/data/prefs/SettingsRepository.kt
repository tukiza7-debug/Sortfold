package com.sortfold.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sortfold.app.core.model.DateGranularity
import com.sortfold.app.core.model.DuplicatePolicy
import com.sortfold.app.core.model.SortMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val languageTag: String? = null,          // null = system default
    val defaultSortMode: SortMode = SortMode.FILE_TYPE,
    val defaultDuplicatePolicy: DuplicatePolicy = DuplicatePolicy.SKIP,
    val defaultDateGranularity: DateGranularity = DateGranularity.MONTH,
    val defaultDestTreeUri: String? = null,
    val namingStyleIso: Boolean = true,       // "2024-05" vs "May 2024" style labels in UI
    val confirmBeforeApply: Boolean = true,
    val batchFileThreshold: Int = 1000,
    val batchBytesThreshold: Long = 2L * 1024 * 1024 * 1024, // 2 GB
    val undoRetentionDays: Int = 7,           // 1, 7 or 30
    val reduceAnimations: Boolean = false,    // false = follow system
    val notificationsEnabled: Boolean = true,
    val autoSortEnabled: Boolean = false,
    val autoCheckUpdates: Boolean = true,
    val includeFullPathsInExport: Boolean = false,
    val onboardingDone: Boolean = false,
    val crashedLastRun: Boolean = false,
    val jobKilledBySystem: Boolean = false,   // set when a running job was killed -> offer battery exemption
    val lastUpdateCheckAt: Long = 0,
)

/**
 * Settings store. The DataStore is created with a corruption handler: a
 * truncated/garbage preferences file used to crash every settings read (and
 * with it the whole app); now it falls back to defaults.
 */
class SettingsRepository(private val context: Context) {

    private val dataStore: DataStore<Preferences> = dataStoreFor(context)

    companion object {
        /**
         * One DataStore instance per file, process-wide: DataStore refuses
         * multiple active instances on the same file, and tests (plus the real
         * Application + any early accessor) can construct several repositories
         * before the first one is collected.
         */
        @Volatile
        private var cached: Pair<File, DataStore<Preferences>>? = null

        private fun dataStoreFor(context: Context): DataStore<Preferences> {
            val file = File(context.filesDir, "datastore/sortfold_settings.preferences_pb")
            synchronized(this) {
                cached?.let { (f, ds) -> if (f == file) return ds }
                val ds = PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                    produceFile = { file },
                )
                cached = file to ds
                return ds
            }
        }
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val LANGUAGE = stringPreferencesKey("language_tag")
        val DEFAULT_MODE = stringPreferencesKey("default_sort_mode")
        val DEFAULT_POLICY = stringPreferencesKey("default_duplicate_policy")
        val DEFAULT_GRANULARITY = stringPreferencesKey("default_date_granularity")
        val DEFAULT_DEST = stringPreferencesKey("default_dest_tree")
        val NAMING_ISO = booleanPreferencesKey("naming_style_iso")
        val CONFIRM = booleanPreferencesKey("confirm_before_apply")
        val BATCH_FILES = intPreferencesKey("batch_file_threshold")
        val BATCH_BYTES = longPreferencesKey("batch_bytes_threshold")
        val UNDO_DAYS = intPreferencesKey("undo_retention_days")
        val REDUCE_ANIM = booleanPreferencesKey("reduce_animations")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val AUTO_SORT = booleanPreferencesKey("auto_sort_enabled")
        val AUTO_UPDATE = booleanPreferencesKey("auto_check_updates")
        val FULL_PATHS = booleanPreferencesKey("include_full_paths")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val CRASHED = booleanPreferencesKey("crashed_last_run")
        val JOB_KILLED = booleanPreferencesKey("job_killed_by_system")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_at")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { p -> toSettings(p) }

    fun toSettings(p: Preferences): AppSettings = AppSettings(
        themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        dynamicColor = p[Keys.DYNAMIC] ?: false,
        languageTag = p[Keys.LANGUAGE],
        defaultSortMode = p[Keys.DEFAULT_MODE]?.let { runCatching { SortMode.valueOf(it) }.getOrNull() } ?: SortMode.FILE_TYPE,
        defaultDuplicatePolicy = p[Keys.DEFAULT_POLICY]?.let { runCatching { DuplicatePolicy.valueOf(it) }.getOrNull() } ?: DuplicatePolicy.SKIP,
        defaultDateGranularity = p[Keys.DEFAULT_GRANULARITY]?.let { runCatching { DateGranularity.valueOf(it) }.getOrNull() } ?: DateGranularity.MONTH,
        defaultDestTreeUri = p[Keys.DEFAULT_DEST],
        namingStyleIso = p[Keys.NAMING_ISO] ?: true,
        confirmBeforeApply = p[Keys.CONFIRM] ?: true,
        batchFileThreshold = p[Keys.BATCH_FILES] ?: 1000,
        batchBytesThreshold = p[Keys.BATCH_BYTES] ?: 2L * 1024 * 1024 * 1024,
        undoRetentionDays = p[Keys.UNDO_DAYS]?.coerceIn(1, 30) ?: 7,
        reduceAnimations = p[Keys.REDUCE_ANIM] ?: false,
        notificationsEnabled = p[Keys.NOTIFICATIONS] ?: true,
        autoSortEnabled = p[Keys.AUTO_SORT] ?: false,
        autoCheckUpdates = p[Keys.AUTO_UPDATE] ?: true,
        includeFullPathsInExport = p[Keys.FULL_PATHS] ?: false,
        onboardingDone = p[Keys.ONBOARDING] ?: false,
        crashedLastRun = p[Keys.CRASHED] ?: false,
        jobKilledBySystem = p[Keys.JOB_KILLED] ?: false,
        lastUpdateCheckAt = p[Keys.LAST_UPDATE_CHECK] ?: 0L,
    )

    suspend fun snapshot(): AppSettings = settings.first()

    suspend fun setThemeMode(v: ThemeMode) = edit { it[Keys.THEME] = v.name }
    suspend fun setDynamicColor(v: Boolean) = edit { it[Keys.DYNAMIC] = v }
    suspend fun setLanguageTag(v: String?) = edit { if (v == null) it.remove(Keys.LANGUAGE) else it[Keys.LANGUAGE] = v }
    suspend fun setDefaultSortMode(v: SortMode) = edit { it[Keys.DEFAULT_MODE] = v.name }
    suspend fun setDefaultDuplicatePolicy(v: DuplicatePolicy) = edit { it[Keys.DEFAULT_POLICY] = v.name }
    suspend fun setDefaultDateGranularity(v: DateGranularity) = edit { it[Keys.DEFAULT_GRANULARITY] = v.name }
    suspend fun setDefaultDestTreeUri(v: String?) = edit { if (v == null) it.remove(Keys.DEFAULT_DEST) else it[Keys.DEFAULT_DEST] = v }
    suspend fun setNamingStyleIso(v: Boolean) = edit { it[Keys.NAMING_ISO] = v }
    suspend fun setConfirmBeforeApply(v: Boolean) = edit { it[Keys.CONFIRM] = v }
    suspend fun setBatchThresholds(files: Int, bytes: Long) = edit {
        it[Keys.BATCH_FILES] = files.coerceAtLeast(1)
        it[Keys.BATCH_BYTES] = bytes.coerceAtLeast(1L)
    }
    suspend fun setUndoRetentionDays(v: Int) = edit { it[Keys.UNDO_DAYS] = v.coerceIn(1, 30) }
    suspend fun setReduceAnimations(v: Boolean) = edit { it[Keys.REDUCE_ANIM] = v }
    suspend fun setNotificationsEnabled(v: Boolean) = edit { it[Keys.NOTIFICATIONS] = v }
    suspend fun setAutoSortEnabled(v: Boolean) = edit { it[Keys.AUTO_SORT] = v }
    suspend fun setAutoCheckUpdates(v: Boolean) = edit { it[Keys.AUTO_UPDATE] = v }
    suspend fun setIncludeFullPaths(v: Boolean) = edit { it[Keys.FULL_PATHS] = v }
    suspend fun setOnboardingDone(v: Boolean) = edit { it[Keys.ONBOARDING] = v }
    suspend fun setCrashedLastRun(v: Boolean) = edit { it[Keys.CRASHED] = v }
    suspend fun setJobKilledBySystem(v: Boolean) = edit { it[Keys.JOB_KILLED] = v }
    suspend fun setLastUpdateCheckAt(v: Long) = edit { it[Keys.LAST_UPDATE_CHECK] = v }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }
}
