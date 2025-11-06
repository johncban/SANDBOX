# Debug-specific ProGuard rules for PassBook Password Manager
# These rules are applied only during debug builds to facilitate easier debugging

# Keep all debug information
-keepattributes SourceFile,LineNumberTable,LocalVariableTable,LocalVariableTypeTable

# Keep all class names for easier debugging
-keepnames class ** { *; }

# Keep all method names for stack traces
-keepclassmembernames class * {
    <methods>;
}

# Don't obfuscate in debug builds
-dontobfuscate

# Keep test classes and methods
-keep class **Test { *; }
-keep class **Tests { *; }
-keep class **.*Test { *; }
-keep class **.*Tests { *; }

# Keep JUnit and testing framework classes
-keep class org.junit.** { *; }
-keep class junit.** { *; }
-keep class androidx.test.** { *; }

# Keep Timber for debug logging
-keep class timber.log.** { *; }

# Keep Hilt debug information
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.AndroidEntryPoint { *; }

# Preserve debug build configuration
-keep class **.BuildConfig { *; }

# Keep debug-specific annotations
-keepattributes *Annotation*

# Less aggressive optimizations for debug
-optimizations !code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 1

# Verbose ProGuard output for debugging
-verbose