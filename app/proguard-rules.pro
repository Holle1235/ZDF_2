# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class de.zdf.app.** {
    *** Companion;
}
-keepclasseswithmembers class de.zdf.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class de.zdf.app.**$$serializer { *; }
