# Flutter entry points are invoked by its engine.
-keep class io.flutter.** { *; }
-dontwarn io.flutter.embedding.**
# Keep resource parser class names stable while removing unreachable code.
-keepnames class com.reandroid.**
-keep class org.jf.** { *; }
# apksig serializes its annotated ASN.1 models reflectively.
-keepattributes Signature,RuntimeVisibleAnnotations
-keep @interface com.android.apksig.internal.asn1.Asn1Class
-keep @interface com.android.apksig.internal.asn1.Asn1Field
-keep @com.android.apksig.internal.asn1.Asn1Class class com.android.apksig.** {
    public <init>();
    @com.android.apksig.internal.asn1.Asn1Field <fields>;
}
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
