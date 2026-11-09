# Sophia release shrinking rules.

# kotlinx.serialization (StudyAnswer and friends in :core). The library ships its own rules;
# these keep our serializers reachable.
-keepclassmembers @kotlinx.serialization.Serializable class com.krlbryn.sophia.core.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.krlbryn.sophia.core.**$$serializer { *; }

# Anthropic Java SDK: Jackson maps API models by reflection, and its Kotlin module reads Kotlin metadata.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,KotlinMetadata

# Optional dependencies of the SDK and Jackson that Android does not ship and Sophia never calls.
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn javax.lang.model.**
-dontwarn org.slf4j.**
-dontwarn io.swagger.**
-dontwarn com.github.victools.**
-dontwarn com.google.errorprone.**
-dontwarn io.opentelemetry.**
-dontwarn com.standardwebhooks.**
-dontwarn org.w3c.dom.bootstrap.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn kotlin.reflect.jvm.internal.**
