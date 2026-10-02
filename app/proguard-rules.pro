# Keep kotlinx.serialization generated serializers.
-keepclassmembers class com.sortfold.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.sortfold.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# WorkManager workers are instantiated by reflection.
-keep class com.sortfold.app.work.** extends androidx.work.CoroutineWorker { *; }
