package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.util.ModificationTracker

private class CachedFunction4[-T1, -T2, -T3, -T4, +R](name: String, modificationTracker: => ModificationTracker)(f: (T1, T2, T3, T4) => R) extends ((T1, T2, T3, T4) => R) {
  private[this] val cache = new CacheN[(T1, T2, T3, T4), R](f.getClass.getName, name, modificationTracker)

  override def apply(v1: T1, v2: T2, v3: T3, v4: T4): R = cache((v1, v2, v3, v4)) {
    f(v1, v2, v3, v4)
  }
}
