# GitHub Android Client · release 混淆规则（M0-07）
#
# 目标：正式产物必须混淆 + 压缩资源。反编译逆向门槛与包体同时收益。
# 注意：Kotlin 元数据、Ktor 反射、kotlinx-serialization 的生成类若被误删会在运行期崩溃，
# 因此这里只"保留必要项"，不做激进 strip。

# ── 崩溃栈符号化所需 ─────────────────────────────────────────────
# 保留行号与源文件名，否则线上崩溃栈无法映射回源码。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ── kotlinx.serialization ────────────────────────────────────────
# @Serializable 类的 serializer 由编译器生成并通过静态字段反射访问。
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.gy.githubclient.**$$serializer { *; }
-keepclassmembers class com.gy.githubclient.** {
    *** Companion;
}
-keepclasseswithmembers class com.gy.githubclient.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.gy.githubclient.**$$serializer { *; }

# ── Ktor ──────────────────────────────────────────────────────────
# Ktor 使用反射加载引擎与插件，误删会导致运行时找不到类。
-keep class io.ktor.client.engine.okhttp.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.slf4j.**

# ── Room ──────────────────────────────────────────────────────────
# Room 生成 DAO/DB 实现，由数据库名反射实例化。
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ── Koin ──────────────────────────────────────────────────────────
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# ── WorkManager ───────────────────────────────────────────────────
# Worker 由类名反射实例化，混淆会导致任务无法执行。
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# ── Retrofit/OkHttp 保留签名（本项目未直接依赖，留作防御）─────────
-keepattributes Signature
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# ── 保留入口类名（崩溃栈可读 + 系统组件可定位）──────────────────
-keep class com.gy.githubclient.MainActivity { *; }
-keep class com.gy.githubclient.GithubClientApp { *; }

# ── 剔除调试日志 ─────────────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
