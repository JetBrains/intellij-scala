package org.jetbrains.plugins.scala

import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.plugins.scala.caches.CacheInUserData._

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


  // TODO Factory method instead of the ProjectUserDataHolder type class

  def cachedInUserData[E: ProjectUserDataHolder, R >: Null](name: String, dataHolder: E, dependency: => AnyRef, f: () => R): () => R = { () =>
    cacheInUserData0(f.getClass.getName, name, dataHolder, dependency, f)
  }

  def cachedInUserData[E: ProjectUserDataHolder, T1, R >: Null](name: String, dataHolder: E, dependency: => AnyRef, f: T1 => R): T1 => R = { v1 =>
    cacheInUserDataN[E, Tuple1[T1], R](f.getClass.getName, name, dataHolder, dependency, Tuple1(v1), v => f(v._1))
  }

  def cachedInUserData[E: ProjectUserDataHolder, T1, T2, R >: Null](name: String, dataHolder: E, dependency: => AnyRef, f: (T1, T2) => R): (T1, T2) => R = { (v1, v2) =>
    cacheInUserDataN[E, (T1, T2), R](f.getClass.getName, name, dataHolder, dependency, (v1, v2), v => f(v._1, v._2))
  }

  def cachedInUserData[E: ProjectUserDataHolder, T1, T2, T3, R >: Null](name: String, dataHolder: E, dependency: => AnyRef, f: (T1, T2, T3) => R): (T1, T2, T3) => R = { (v1, v2, v3) =>
    cacheInUserDataN[E, (T1, T2, T3), R](f.getClass.getName, name, dataHolder, dependency, (v1, v2, v3), v => f(v._1, v._2, v._3))
  }
}
