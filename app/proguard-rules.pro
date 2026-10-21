-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keepclassmembers class dev.plexlite.tv.data.** {
    *** Companion;
}
-keepclasseswithmembers class dev.plexlite.tv.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
