-dontnote android.net.http.*
-dontnote org.apache.http.**
-keep class org.fossify.** { *; }
-dontwarn org.fossify.**

# Tink references javax.annotation
-dontwarn javax.annotation.**

# mbassy references javax.el
-dontwarn javax.el.**

# smbj references org.ietf.jgss
-dontwarn org.ietf.jgss.**

# SMBJ APIs used via reflection (keep in release/minified builds)
-keepclassmembers class com.hierynomus.smbj.share.DiskShare {
    void rename(java.lang.String, java.lang.String);
    void rename(java.lang.String, java.lang.String, boolean);
    void rm(java.lang.String);
    void rm(java.lang.String, boolean);
    void rmdir(java.lang.String);
    void rmdir(java.lang.String, boolean);
}
