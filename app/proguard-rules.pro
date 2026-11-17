# Flutter entry points are invoked by its engine.
-keep class io.flutter.** { *; }
-dontwarn io.flutter.embedding.**
# APK parsing/signing libraries expose reflective model factories.
-keep class com.reandroid.** { *; }
-keep class org.jf.** { *; }
-keep class com.android.apksig.** { *; }
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
