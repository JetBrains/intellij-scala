package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.util.ModificationTracker

private class CachedFunction1[-T, +R](name: String, modificationTracker: => ModificationTracker)(f: T => R) extends (T => R) {
  private[this] val cache = new CacheN[Tuple1[T], R](f.getClass.getName, name, modificationTracker)

  override def apply(v1: T): R = cache(Tuple1(v1)) {
    f(v1)
  }
}
