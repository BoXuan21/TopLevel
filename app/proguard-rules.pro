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
