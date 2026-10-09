# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.yaz.calculator.** {
    *** Companion;
}
-keepclasseswithmembers class com.yaz.calculator.** {
    kotlinx.serialization.KSerializer serializer(...);
}
