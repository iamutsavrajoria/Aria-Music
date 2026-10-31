# SPDX-License-Identifier: GPL-3.0-only
# Skeleton release rules. Library consumer rules (Room, Hilt, kotlinx.serialization, Media3) cover the rest.
# JVM-only classes that Ktor and its logging transitively reference but Android does not ship:
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
-dontwarn org.slf4j.impl.**
