# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt

# Keep the notification listener service
-keep class com.charlotteservicehub.autotext.service.MissedCallListenerService { *; }

# Keep SharedPreferences keys
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
