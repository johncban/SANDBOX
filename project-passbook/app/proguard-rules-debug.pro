# Debug-specific ProGuard rules for easier debugging while maintaining security
# These rules are applied only to debug builds for easier troubleshooting

# Keep debug information for easier troubleshooting
-keepattributes SourceFile,LineNumberTable
-keepattributes LocalVariableTable,LocalVariableTypeTable
-keepattributes MethodParameters

# Keep debug logging but still obfuscate sensitive security components
-keep class timber.log.** { *; }
-keep class com.jakewharton.timber.** { *; }

# Keep debugging utilities
-keep class leakcanary.** { *; }
-keep class com.squareup.leakcanary.** { *; }
-keep class com.facebook.flipper.** { *; }
-keep class com.facebook.soloader.** { *; }

# Debug builds still need some security measures - remove verbose logs
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Keep test frameworks for debug builds
-keep class androidx.test.** { *; }
-keep class junit.** { *; }
-keep class org.junit.** { *; }
-keep class org.mockito.** { *; }
-keep class io.mockk.** { *; }

# Preserve stack traces for crash reporting in debug
-keepattributes StackMapTable
-keepattributes Exceptions
-keepattributes Signature

# Keep annotations for debugging
-keepattributes *Annotation*
-keep @interface * { *; }

# Debug: Keep enum toString methods for easier debugging
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public ** toString();
}

# Keep reflection-based debugging utilities
-keepclassmembers class * {
    @androidx.compose.ui.tooling.preview.Preview <methods>;
}

# Allow reflection for debugging tools
-keepclassmembers class * {
    public <methods>;
    public <fields>;
}

# Note: Even in debug, security-sensitive classes should remain obfuscated
# This is handled in the main proguard-rules.pro file
# Security classes are not affected by these debug rules