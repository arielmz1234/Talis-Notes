# Ink loads its native code through JNI; keep the Java side of those bindings intact.
-keep class androidx.ink.** { *; }
