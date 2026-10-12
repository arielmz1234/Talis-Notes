# Ink loads its native code through JNI; keep the Java side of those bindings intact.
-keep class androidx.ink.** { *; }

# The document scanner (ML Kit) sets itself up by reflection: shrunk, GmsDocumentScanning.getClient
# crashed with a NullPointerException in release builds (debug builds were fine). Keep it whole.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_document_scanner.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.firebase.components.** { *; }
