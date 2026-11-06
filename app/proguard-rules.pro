# SPDX-License-Identifier: GPL-3.0-only
# Skeleton release rules. Library consumer rules (Room, Hilt, kotlinx.serialization, Media3) cover the rest.
# JVM-only classes that Ktor and its logging transitively reference but Android does not ship:
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
-dontwarn org.slf4j.impl.**
# The PoToken WebView's single JavaScript bridge method is reached by name from page JavaScript (ADR-0002).
-keepclassmembers class app.aria.innertube.potoken.PoTokenWebView$Bridge { @android.webkit.JavascriptInterface <methods>; }
