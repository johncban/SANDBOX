# ============================================
# PassBook - Minimal ProGuard Rules (No Errors)
# ============================================

# ========== CORE ANDROID ==========
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# ========== YOUR APP ==========
# Keep MainActivity
-keep class com.jcb.passbook.MainActivity { *; }

# Keep all your app classes (adjust package names as needed)
-keep class com.jcb.passbook.** { *; }

# ========== COMPOSE ==========
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Composable functions
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ========== NAVIGATION ==========
-keep class androidx.navigation.** { *; }

# ========== LIFECYCLE ==========
-keep class androidx.lifecycle.** { *; }
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ========== HILT ==========
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class **_HiltModules { *; }
-keep class **_Factory { *; }

# Keep Hilt ViewModels
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * {
    <init>(...);
}

# Keep @Inject constructors
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}

# ========== ROOM ==========
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# ========== KOTLIN ==========
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ========== SECURITY (If you have these) ==========
# Only uncomment if these packages exist in your project
# -keep class androidx.security.crypto.** { *; }
# -keep class net.sqlcipher.** { *; }
# -keep class com.lambdapioneer.argon2kt.** { *; }
# -keep class com.scottyab.rootbeer.** { *; }

# ========== GENERAL ==========
# Enum support
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelable
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# ========== WARNINGS ==========
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn kotlin.jvm.internal.**
-dontwarn javax.annotation.**
