package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.util.ModificationTracker

private class CachedFunction2[-T1, -T2, +R](name: String, modificationTracker: => ModificationTracker)(f: (T1, T2) => R) extends ((T1, T2) => R) {
  private[this] val cache = new CacheN[(T1, T2), R](f.getClass.getName, name, modificationTracker)

  override def apply(v1: T1, v2: T2): R = cache((v1, v2)) {
    f(v1, v2)
  }
}
