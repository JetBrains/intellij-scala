package org.jetbrains.plugins.scala

import com.intellij.openapi.util.ModificationTracker

package object caches {

  def cached[R](name: String, modificationTracker: => ModificationTracker, f: () => R): () => R =
    new CachedFunction0[R](name, modificationTracker)(f)

  def cached[T1, R](name: String, modificationTracker: => ModificationTracker, f: T1 => R): T1 => R =
    new CachedFunction1[T1, R](name, modificationTracker)(f)

  def cached[T1, T2, R](name: String, modificationTracker: => ModificationTracker, f: (T1, T2) => R): (T1, T2) => R =
    new CachedFunction2[T1, T2, R](name, modificationTracker)(f)

  def cached[T1, T2, T3, R](name: String, modificationTracker: => ModificationTracker, f: (T1, T2, T3) => R): (T1, T2, T3) => R =
    new CachedFunction3[T1, T2, T3, R](name, modificationTracker)(f)

  def cached[T1, T2, T3, T4, R](name: String, modificationTracker: => ModificationTracker, f: (T1, T2, T3, T4) => R): (T1, T2, T3, T4) => R =
    new CachedFunction4[T1, T2, T3, T4, R](name, modificationTracker)(f)

}
