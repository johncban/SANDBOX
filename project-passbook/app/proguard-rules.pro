# ============================================
# PassBook Password Manager - ProGuard Rules
# ✅ FIXED VERSION: Prevents Compose lock verification issues
# ============================================

# ========== LOGGING RULES ==========
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep Timber for logging
-keep class timber.log.Timber { *; }
-keep class timber.log.Timber$Tree { *; }
-keepclassmembers class timber.log.Timber {
    public static void e(...);
    public static void w(...);
}

# ========== COMPOSE FIX - CRITICAL ==========
# ✅ Prevent lock verification issues with Compose
-keep class androidx.compose.runtime.snapshots.** { *; }
-dontwarn androidx.compose.runtime.snapshots.**
-keep class androidx.compose.runtime.** { *; }
-keep class kotlin.Metadata { *; }
-keep class **ComposerKt { *; }

# ========== SECURITY-CRITICAL CLASSES ==========
-keep class com.jcb.passbook.security.** { *; }
-keep class com.jcb.passbook.data.local.database.entities.** { *; }
-keep class com.jcb.passbook.core.security.** { *; }

# Audit logging
-keep class com.jcb.passbook.security.audit.** { *; }
-keepclassmembers class com.jcb.passbook.security.audit.** {
    public <methods>;
    public <fields>;
}

# Cryptographic operations
-keep class com.jcb.passbook.security.crypto.** { *; }
-keepclassmembers class com.jcb.passbook.security.crypto.** {
    native <methods>;
    public <methods>;
    protected <methods>;
}

# Root/tamper detection
-keep class com.jcb.passbook.security.detection.** { *; }
-keepclassmembers class com.jcb.passbook.security.detection.** {
    public <methods>;
}

# ========== THIRD-PARTY SECURITY LIBRARIES ==========

# AndroidX Security
-keep class androidx.security.crypto.** { *; }
-keep interface androidx.security.crypto.** { *; }

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keepclassmembers class net.sqlcipher.** {
    native <methods>;
    public <methods>;
}

# Argon2
-keep class com.lambdapioneer.argon2kt.** { *; }
-keepclassmembers class com.lambdapioneer.argon2kt.** {
    native <methods>;
    public <methods>;
}

# RootBeer
-keep class com.scottyab.rootbeer.** { *; }
-keepclassmembers class com.scottyab.rootbeer.** {
    native <methods>;
    public <methods>;
}

# ========== HILT DEPENDENCY INJECTION ==========
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep class **_HiltModules { *; }
-keep class **_HiltModules$* { *; }
-keep class * extends dagger.hilt.internal.GeneratedEntryPoint
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keep class **$HiltWrapper { *; }

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ========== ROOM DATABASE ==========
-keep class androidx.room.** { *; }
-keep class androidx.sqlite.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep class **_Impl { *; }
-dontwarn androidx.room.paging.**

# ========== KOTLINX SERIALIZATION ==========
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-dontnote kotlinx.serialization.SerializationKt

-keep,includedescriptorclasses class com.jcb.passbook.**$$serializer { *; }
-keepclassmembers class com.jcb.passbook.** {
    *** Companion;
}
-keepclasseswithmembers class com.jcb.passbook.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep @kotlinx.serialization.Serializable class ** {
    public <fields>;
    public <methods>;
    *** Companion;
    *** $serializer;
}

# ========== GENERAL ANDROID ==========
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelable
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ========== OPTIMIZATION ==========
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-overloadaggressively
-repackageclasses 'o'
-allowaccessmodification
-mergeinterfacesaggressively

# ========== WARNING SUPPRESSIONS ==========
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
