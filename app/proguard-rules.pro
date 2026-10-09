# kotlinx.serialization ships its own consumer rules; keep our @Serializable models explicitly as well.
-keep,includedescriptorclasses class com.iceberg.keyboard.core.**$$serializer { *; }
-keepclassmembers class com.iceberg.keyboard.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.iceberg.keyboard.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# The IME service and settings activity are referenced from the manifest / method.xml.
-keep class com.iceberg.keyboard.ime.IcebergImeService
