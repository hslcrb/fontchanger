# Shizuku
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
