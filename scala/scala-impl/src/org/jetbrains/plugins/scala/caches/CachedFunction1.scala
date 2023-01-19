package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.util.ModificationTracker

private class CachedFunction1[-T1, +R](name: String, modificationTracker: => ModificationTracker)(f: T1 => R) extends (T1 => R) {
  private[this] val cache = new CacheN[Tuple1[T1], R](f.getClass.getName, name, modificationTracker)

  override def apply(v1: T1): R = cache(Tuple1(v1)) {
    f(v1)
  }
}
