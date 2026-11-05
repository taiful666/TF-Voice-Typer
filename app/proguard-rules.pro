# Room entities / DAO are generated; keep model classes readable in crash logs
-keep class com.taiful.tfvoicetyper.data.db.** { *; }
-keepattributes SourceFile,LineNumberTable
# Offline voice engine: its native (JNI) code looks these classes and fields up by name, so they must not be renamed or removed
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
