# Instrumentation lives in another APK. Retain its Kotlin public entry points
# (including static facade classes such as LazyKt and BuildersKt), since R8
# cannot see the runner's external calls. All other libraries retain normal
# shrinking/optimization; release signing and validation remain enabled.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.* { *; }
-keep class androidx.concurrent.futures.** { *; }
