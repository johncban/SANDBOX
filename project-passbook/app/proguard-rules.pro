# Add project specific ProGuard rules here.

# Keep all classes in the main package
-keep class com.jcb.passbook.** { *; }

# Hilt
-dontwarn com.google.dagger.hilt.**
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep class dagger.hilt.android.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Security & Cryptography
-keep class androidx.security.crypto.** { *; }
-keep class com.lambdapioneer.argon2kt.** { *; }
-keep class net.zetetic.database.** { *; }

# Google Tink Cryptography - COMPLETE FIX FOR ALL R8 BUILD FAILURES
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-dontwarn com.google.errorprone.annotations.**

# Keep Tink classes for proper crypto functionality
-keep class com.google.crypto.tink.** { *; }

# Google API Client HTTP (Optional Tink dependency - suppress warnings)
-dontwarn com.google.api.client.http.GenericUrl
-dontwarn com.google.api.client.http.HttpHeaders
-dontwarn com.google.api.client.http.HttpRequest
-dontwarn com.google.api.client.http.HttpRequestFactory
-dontwarn com.google.api.client.http.HttpResponse
-dontwarn com.google.api.client.http.HttpTransport
-dontwarn com.google.api.client.http.javanet.NetHttpTransport$Builder
-dontwarn com.google.api.client.http.javanet.NetHttpTransport
-dontwarn com.google.api.client.http.**

# Google GSON (Optional Tink dependency - suppress warnings)
-dontwarn com.google.gson.JsonArray
-dontwarn com.google.gson.JsonElement
-dontwarn com.google.gson.JsonNull
-dontwarn com.google.gson.JsonObject
-dontwarn com.google.gson.JsonParseException
-dontwarn com.google.gson.JsonPrimitive
-dontwarn com.google.gson.TypeAdapter
-dontwarn com.google.gson.internal.Streams
-dontwarn com.google.gson.stream.JsonReader
-dontwarn com.google.gson.stream.JsonToken
-dontwarn com.google.gson.stream.JsonWriter
-dontwarn com.google.gson.**

# Joda Time (Optional Tink dependency - suppress warnings)
-dontwarn org.joda.time.Instant
-dontwarn org.joda.time.**

# Timber
-dontwarn org.jetbrains.annotations.**

# Compose
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.runtime.**

# Root detection
-keep class com.scottyab.rootbeer.** { *; }

# General Android
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception