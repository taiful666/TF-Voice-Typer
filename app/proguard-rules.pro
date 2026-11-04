# Room entities / DAO are generated; keep model classes readable in crash logs
-keep class com.taiful.tfvoicetyper.data.db.** { *; }
-keepattributes SourceFile,LineNumberTable
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
