# The app is open source: obfuscated stack traces (q2.b.q / Unknown Source)
# make error reports useless to users and to us. Keep shrinking and
# optimization, but preserve names and line numbers so the Error Library and
# exports are readable. No mapping file is needed or published.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
-keepclassmembers class com.sortfold.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.sortfold.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# WorkManager workers are instantiated by reflection.
-keep class com.sortfold.app.work.** extends androidx.work.CoroutineWorker { *; }
