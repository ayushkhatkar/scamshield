# Retrofit + kotlinx.serialization
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.scamshield.**$$serializer { *; }
-keepclassmembers class com.scamshield.** { *** Companion; }
-keepclasseswithmembers class com.scamshield.** { kotlinx.serialization.KSerializer serializer(...); }
# Play Billing
-keep class com.android.vending.billing.** { *; }
