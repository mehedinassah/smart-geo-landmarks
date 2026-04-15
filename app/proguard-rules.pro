# Add project specific ProGuard rules here.
-keep class com.smartlandmarks.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
