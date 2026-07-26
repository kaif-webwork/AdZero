# ─────────────────────────────────────────────────────────────────────────────
# AdZero Security & Obfuscation ProGuard Rules
# ─────────────────────────────────────────────────────────────────────────────

# Keep NewPipe Extractor classes (uses reflection internally)
-keep class org.schabi.newpipe.extractor.** { *; }
-keep interface org.schabi.newpipe.extractor.** { *; }

# Keep Media3 ExoPlayer classes
-keep class androidx.media3.** { *; }

# Strip internal debug log statements in production
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Preserve lines and attributes for stack traces
-keepattributes SourceFile,LineNumberTable,*Annotation*

# Security: Hide internal package structure
-repackageclasses 'com.adzero.app.s'
-allowaccessmodification
