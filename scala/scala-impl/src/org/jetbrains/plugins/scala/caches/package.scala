package org.jetbrains.plugins.scala

import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.caches.CacheInUserData._
import org.jetbrains.plugins.scala.caches.CacheWithRecursionGuard.{cacheWithRecursionGuard0, cacheWithRecursionGuardN}

package object caches {

  // TODO Detect control flow exceptions

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

  def cachedInUserData[E: ProjectUserDataHolder, R](name: String, dataHolder: E, dependency: => AnyRef)(f: => R): R =
    cacheInUserData0((() => f).getClass.getName, name, dataHolder, dependency, f)

  def cachedInUserData[E: ProjectUserDataHolder, T <: Product, R](name: String, dataHolder: E, dependency: => AnyRef, v: T)(f: => R): R =
    cacheInUserDataN[E, T, R]((() => f).getClass.getName, name, dataHolder, dependency, v, f)

  // TODO (defaultValue: => R) parameter list

  def cachedWithRecursionGuard[R](name: String, element: PsiElement, defaultValue: => R, dependency: => AnyRef)(f: => R): R =
    cacheWithRecursionGuard0((() => f).getClass.getName, name, element, defaultValue, dependency, f)

  def cachedWithRecursionGuard[T <: Product, R](name: String, element: PsiElement, defaultValue: => R, dependency: => AnyRef, v: T)(f: => R): R =
    cacheWithRecursionGuardN[T, R]((() => f).getClass.getName, name, element, defaultValue, dependency, v, f)
}
