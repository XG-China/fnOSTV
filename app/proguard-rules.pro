# ---------------------------------------------------------------------------
# 通用保留规则（debug / release 共用）
# ---------------------------------------------------------------------------

# libVLC 通过 JNI 按类名/方法名查找 Java 层实现，必须保留名字。
-keep class org.videolan.** { *; }
-keepclassmembers class org.videolan.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# 反射/泛型/注解所需元数据
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# 系统组件（清单里声明的 Activity 由 AGP 自动保留，这里显式兜底）
-keep class com.fnostv.android4.MainActivity { *; }
-keep class com.fnostv.android4.SettingsActivity { *; }
