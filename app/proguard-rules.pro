# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ------------------------------------------------------------
# Release R8/ProGuard rules
# ------------------------------------------------------------

# Keep enough metadata for libraries that inspect annotations, generic
# signatures, or Kotlin declarations at runtime, plus useful release stack
# traces after minification.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-keep class kotlin.Metadata { *; }

-renamesourcefileattribute SourceFile

# JNI native method names are resolved from the Java/Kotlin package, class,
# and method names when RegisterNatives is not used. These two classes are
# exported from app/src/main/cpp with Java_com_hero_... symbols, so their
# binary names and native methods must not be obfuscated.
-keep class com.hero.ziggymusic.audio.AudioProcessorChainController {
    native <methods>;
}

-keep class com.hero.ziggymusic.audio.BufferAddressHelper {
    native <methods>;
}

# General safety net for future JNI entry points.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Data Binding calls generated binding adapters by annotation. Keep adapter
# method names and signatures so layout binding remains stable after R8.
-keepclassmembers class * {
    @androidx.databinding.BindingAdapter <methods>;
}

# Otto discovers @Subscribe/@Produce methods reflectively. There are no
# current subscribers in the project, but these rules prevent future release
# only event-bus failures when minification is enabled.
-keepclassmembers class * {
    @com.squareup.otto.Subscribe <methods>;
    @com.squareup.otto.Produce <methods>;
}

# Parcelable support used by models passed through Bundles/Intents.
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
