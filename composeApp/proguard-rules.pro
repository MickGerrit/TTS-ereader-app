# Release shrinking rules.
#
# The roadmap's warning was right: Readium and the speech engine both reach for
# things by name, and R8 cannot see those references. Everything kept below is kept
# for a reason that is written down, so the next person can test whether it is still
# true rather than guessing.

# ── Readium ───────────────────────────────────────────────────────────────
# The navigator is instantiated by name through the FragmentFactory, and its
# JavaScript bridge calls back into annotated methods from inside a WebView.
-keep class org.readium.r2.navigator.** { *; }
-keep class org.readium.r2.shared.** { *; }
-keep class org.readium.r2.streamer.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Publication models are (de)serialised through kotlinx.serialization, which
# resolves generated serializers reflectively.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class *
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# ── Fragments ─────────────────────────────────────────────────────────────
# Restored by name after process death.
-keep public class * extends androidx.fragment.app.Fragment

# ── Our own seams ─────────────────────────────────────────────────────────
# PlaybackService is named in the manifest and started by Intent.
-keep class nl.lector.engine.PlaybackService { *; }

# ── Noise ─────────────────────────────────────────────────────────────────
# Optional dependencies Readium's transitive graph mentions but never loads on
# Android. Warnings here are not missing code, they are code that is not shipped.
-dontwarn org.slf4j.**
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
