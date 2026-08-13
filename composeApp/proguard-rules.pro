# R8 rules for the release build.
#
# Most of the stack needs nothing here: SQLDelight generates plain code with no reflection,
# Koin's constructor DSL (`viewModelOf(::Foo)`) is resolved at compile time rather than by
# name, and ML Kit, Glance and WorkManager all ship their own consumer rules inside their
# AARs. What is left is the handful of classes something looks up by name at runtime, which
# R8 cannot see a reference to and will therefore remove or rename.
#
# Anything added here should say why. A keep rule with no reason is a rule nobody can ever
# safely delete.

# WorkManager instantiates workers reflectively from the class name recorded in its database.
# Koin's factory normally builds them, but WorkManager falls back to the default factory —
# and that fallback is exactly the path a renamed class breaks, silently, on the next
# scheduled run rather than at build time.
-keep class com.anish.expirydatereminder.notifications.ExpiryReminderWorker { <init>(...); }

# Glance rebuilds widget state through RemoteViews across process boundaries.
-keep class com.anish.expirydatereminder.widget.ExpiryWidgetReceiver { *; }

# ML Kit discovers its own modules at runtime: the manifest lists ComponentRegistrar class
# names, and it instantiates each one reflectively through its no-argument constructor. R8
# has no visible reference to those constructors, strips them, and the failure is silent —
# the app starts, logs `NoSuchMethodException` at WARN, and scanning simply never works.
# Caught by running the minified build; a debug build cannot show this.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
-keep class com.google.mlkit.common.internal.** { <init>(); }

# The GenAI Prompt API is a beta optional dependency, resolved at runtime and absent on most
# devices. Without this, R8 fails the build on references it cannot resolve rather than
# letting the availability check do its job.
-dontwarn com.google.mlkit.genai.**

# Kotlin coroutines' internal service loader entries, referenced only from a manifest file
# inside the jar.
-dontwarn kotlinx.coroutines.**

# Keeps line numbers in Play Console crash reports. Without this a stack trace is a list of
# obfuscated names, and the mapping file is uploaded anyway.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
