package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.util.ModificationTracker

private class CachedFunction0[+R](name: String, modificationTracker: => ModificationTracker)(f: () => R) extends (() => R) {
  private[this] val cache = new Cache0[R](f.getClass.getName, name, modificationTracker)

  def apply(): R = cache {
    f()
  }
}
