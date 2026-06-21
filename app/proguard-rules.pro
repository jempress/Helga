# Apache MINA FtpServer and the MINA core networking library do internal
# reflection-based wiring (config, listeners, command factories). Keep them
# fully intact so R8 doesn't strip anything the server needs at runtime.
-keep class org.apache.ftpserver.** { *; }
-keep class org.apache.mina.** { *; }
-keep interface org.apache.ftpserver.** { *; }
-keep interface org.apache.mina.** { *; }
-dontwarn org.apache.ftpserver.**
-dontwarn org.apache.mina.**
-dontwarn org.slf4j.**
-dontwarn javax.management.**
-dontwarn javax.naming.**

# zxing QR generation
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
