/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
// Modified for Aria: package renamed; NewApi lint suppressed on RuntimeShader creation (glass effects only run when GlassResolver reports API 33+).
package app.aria.designsystem.backdrop

import android.annotation.SuppressLint
import org.intellij.lang.annotations.Language

sealed interface RuntimeShaderCache {

  fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

  private val runtimeShaders = mutableMapOf<String, RuntimeShader>()

  @SuppressLint("NewApi") // callers only reach this on API 33+ (GlassResolver gates the effect)
  override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
    return runtimeShaders.getOrPut(key) { RuntimeShader(string) }
  }

  fun clear() {
    runtimeShaders.clear()
  }
}
