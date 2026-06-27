# ProGuard rules for AirLLM Android

# MediaPipe Tasks GenAI
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep our data classes
-keep class com.airllm.model.** { *; }
-keep class com.airllm.viewmodel.** { *; }
