# Release shrinking (R8). Libraries ship their own rules (Room, WorkManager, Glance, OkHttp,
# kotlinx.serialization, ML Kit); these cover the app's own code.

# Entities, plans, backups and other stored/serialized data: keep names and fields as they are
-keep class com.toplevel.app.data.** { *; }
-keepclassmembers enum com.toplevel.app.** { *; }

# Workers and receivers are created by class name
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class com.toplevel.app.widget.** { *; }
-keep class com.toplevel.app.update.InstallReceiver { *; }

# kotlinx.serialization: generated serializers of the app's classes
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class com.toplevel.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Readable stack traces in crash reports
-keepattributes SourceFile, LineNumberTable

# ML Kit barcode scanner: the bundled native reader (barhopper) looks up Java classes by name over JNI,
# and ML Kit finds its components by reflection. Shrinking/renaming them made scans hang in release builds.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode_bundled.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.libraries.barhopper.** { *; }
-keep class com.google.photos.vision.barhopper.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-dontwarn com.google.mlkit.**
