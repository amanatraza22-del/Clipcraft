# Keep Firebase Firestore / Auth / AI Logic models and serialization intact
-keepclassmembers class com.example.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Strip debug logging in release builds to prevent telemetry leakage
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
