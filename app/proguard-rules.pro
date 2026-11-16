# YouTube player JS bridge
-keep class com.pierfrancescosoffritti.androidyoutubeplayer.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# kotlinx.serialization models
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class app.gideon.data.**$$serializer { *; }
-keepclassmembers class app.gideon.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
