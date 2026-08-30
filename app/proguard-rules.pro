# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
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

# Tink (via security-crypto) references Error Prone annotations that are not
# on the runtime classpath. The standard flavor gets these via Play Services
# consumer rules; the fdroid flavor does not.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# WorkManager instantiates input mergers by their persisted class name. AGP 9/R8 can remove
# their no-arg constructors even though WorkManager's consumer rules preserve the classes,
# causing Glance widget update work to fail before rendering the saved configuration.
-keep class androidx.work.OverwritingInputMerger {
    public <init>();
}
-keep class androidx.work.ArrayCreatingInputMerger {
    public <init>();
}
