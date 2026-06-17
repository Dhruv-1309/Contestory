# Keep GSON related classes from being obfuscated
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.example.contesttracker.** { *; }
-keepclassmembers class com.example.contesttracker.** { <fields>; }
