# kotlinx-serialization: keep generated serializers for the save-data model.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.populong.bubbleshooter.core.**$$serializer { *; }
-keepclassmembers class com.populong.bubbleshooter.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.populong.bubbleshooter.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
