# --- Kotlinx Serialization ---
# The kotlinx.serialization compiler plugin generates $serializer nested
# classes and Companion serializer() methods; R8 has to leave those reachable
# or deserialization fails at runtime with "ClassNotFound serializer".
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep $Companion fields of classes marked @Serializable.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep the serializer() method on companions of @Serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep INSTANCE.serializer() on @Serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Ktor + OkHttp ---
# Ktor uses reflection to resolve engine/plugins; simplest safe rule.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-keep class okhttp3.internal.platform.** { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn javax.annotation.**

# --- Room ---
# Room's @Entity / @Dao annotations drive generated code; keep the annotated
# classes so the generated Impl classes can cast to them.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class androidx.room.** { *; }

# --- AndroidX Media3 (ExoPlayer) ---
# Media3 ships consumer rules in its AAR, but we suppress a few noisy warns
# for optional format modules we don't actually pull in.
-dontwarn org.checkerframework.checker.nullness.qual.**

# --- Our models ---
# Our DTOs + domain models are all @Serializable, covered by the rules above.
# Nothing app-specific needed here.
