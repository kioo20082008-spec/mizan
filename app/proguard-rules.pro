# Crash reports: keep the original source file name and line numbers.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# --- Room -------------------------------------------------------------------
# Room generates implementations/subclasses at compile time and looks them up
# by name at runtime, so R8 must not strip or rename them.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# --- WorkManager ------------------------------------------------------------
# Workers are instantiated reflectively by WorkerFactory.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(...);
}
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# --- Glance / App Widgets ---------------------------------------------------
# Glance composes/instantiates widget receivers and composables reflectively.
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# --- Classes referenced from AndroidManifest.xml ----------------------------
# Activities, receivers, services and providers named in the manifest are
# loaded by name by the framework.
-keep class com.mizan.money.MoneyApp { *; }
-keep class com.mizan.money.MainActivity { *; }
-keep class com.mizan.money.sms.SmsReceiver { *; }
-keep class com.mizan.money.widget.MizanWidgetReceiver { *; }
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider
-keep class * extends android.app.Application
-keep class * extends android.appwidget.AppWidgetProvider

# --- Kotlin / reflection ----------------------------------------------------
# Serialization and reflection-used metadata.
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleAnnotations,RuntimeInvisibleParameterAnnotations
