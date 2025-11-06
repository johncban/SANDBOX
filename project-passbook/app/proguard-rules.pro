# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ========== PASSBOOK SECURITY-SPECIFIC RULES ==========

# CRITICAL: Keep all security-related classes from obfuscation/removal
-keep class com.jcb.passbook.security.** { *; }
-keep class com.jcb.passbook.data.local.database.entities.** { *; }
-keep class com.jcb.passbook.core.security.** { *; }

# Keep audit logging intact for forensic analysis
-keep class com.jcb.passbook.security.audit.** { *; }
-keepclassmembers class com.jcb.passbook.security.audit.** {
    public <methods>;
    public <fields>;
}

# Cryptographic classes must not be obfuscated
-keep class com.jcb.passbook.security.crypto.** { *; }
-keepclassmembers class com.jcb.passbook.security.crypto.** {
    native <methods>;
    public <methods>;
    protected <methods>;
}

# Root/tamper detection classes
-keep class com.jcb.passbook.security.detection.** { *; }
-keepclassmembers class com.jcb.passbook.security.detection.** {
    public <methods>;
}

# Keep AndroidX Security classes
-keep class androidx.security.crypto.** { *; }
-keep interface androidx.security.crypto.** { *; }

# Keep SQLCipher classes
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keepclassmembers class net.sqlcipher.** {
    native <methods>;
    public <methods>;
}

# Keep Argon2 native methods
-keep class com.lambdapioneer.argon2kt.** { *; }
-keepclassmembers class com.lambdapioneer.argon2kt.** {
    native <methods>;
    public <methods>;
}

# Keep RootBeer detection
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

# Keep Hilt generated classes
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keep class **$HiltWrapper { *; }

# ========== ROOM DATABASE ==========
-keep class androidx.room.** { *; }
-keep class androidx.sqlite.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# Keep Room generated classes
-keep class **_Impl { *; }
-keep class **$Companion { *; }

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

# Keep @Serializable classes
-keep @kotlinx.serialization.Serializable class ** {
    public <fields>;
    public <methods>;
    *** Companion;
    *** $serializer;
}

# ========== COMPOSE ==========
-keep class androidx.compose.** { *; }
-keep class kotlin.Metadata { *; }

# Keep Compose compiler generated classes
-keep class **ComposerKt { *; }
-keep class **$Companion { *; }

# ========== GENERAL ANDROID ==========
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes *Annotation*

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ========== SECURITY HARDENING ==========
# Remove debug information in release builds
-keepattributes !LocalVariableTable
-keepattributes !LocalVariableTypeTable

# Remove logging calls in release (except security audit logs)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Keep Timber for security logging but remove debug trees
-keep class timber.log.Timber { *; }
-keep class com.jcb.passbook.utils.logging.RestrictiveReleaseTree { *; }

# Remove test-related code from release builds
-dontwarn junit.**
-dontwarn org.junit.**
-dontwarn org.hamcrest.**
-dontwarn com.squareup.javawriter.JavaWriter

# ========== OBFUSCATION ENHANCEMENT ==========
# Use aggressive obfuscation for non-security classes
-overloadaggressively
-repackageclasses 'o'
-allowaccessmodification
-mergeinterfacesaggressively

# But preserve security-critical debugging info
-keepattributes SourceFile,LineNumberTable

# ========== OPTIMIZATION ==========
# Enable all optimizations
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5

# ========== WARNING SUPPRESSIONS ==========
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**