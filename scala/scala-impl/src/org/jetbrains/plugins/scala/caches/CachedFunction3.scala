package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.util.ModificationTracker

private class CachedFunction3[-T1, -T2, -T3, +R](name: String, modificationTracker: => ModificationTracker)(f: (T1, T2, T3) => R) extends ((T1, T2, T3) => R) {
  private[this] val cache = new CacheN[(T1, T2, T3), R](f.getClass.getName, name, modificationTracker)

  override def apply(v1: T1, v2: T2, v3: T3): R = cache((v1, v2, v3)) {
    f(v1, v2, v3)
  }
}
