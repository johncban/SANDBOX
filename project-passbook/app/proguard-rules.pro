# Security components
-keep class com.jcb.passbook.security.** { *; }
-keep class com.jcb.passbook.data.local.database.entities.** { *; }

# SQLCipher
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.jcb.passbook.**$$serializer { *; }
-keepclassmembers class com.jcb.passbook.** {
    *** Companion;
}
-keepclasseswithmembers class com.jcb.passbook.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep audit entity serialization
-keep class com.jcb.passbook.data.local.database.entities.AuditEntry { *; }
-keep class com.jcb.passbook.data.local.database.entities.AuditEventType { *; }
-keep class com.jcb.passbook.data.local.database.entities.AuditOutcome { *; }

# Android Keystore
-keep class android.security.keystore.** { *; }
-dontwarn android.security.keystore.**

# Biometric
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# Room database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Timber
-dontwarn org.jetbrains.annotations.**

# Hilt
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.InstallIn

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Security-specific rules
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep integrity checking methods
-keepclassmembers class com.jcb.passbook.data.local.database.entities.AuditEntry {
    public *** generateChecksum();
    public *** generateCanonicalData();
}