package org.jetbrains.plugins.scala

import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.caches.CacheInUserData._
import org.jetbrains.plugins.scala.caches.CacheWithRecursionGuard.{cacheWithRecursionGuard0, cacheWithRecursionGuardN}
import org.jetbrains.plugins.scala.caches.stats.Tracer

package object caches {

  // TODO Detect control flow exceptions

  def cached[R](name: String, modificationTracker: => ModificationTracker, f: () => R): () => R = {
    val cache = new Cache0[R](f.getClass.getName, name, modificationTracker)
    () => cache { f() }
  }

  def cached[T1, R](name: String, modificationTracker: => ModificationTracker, f: T1 => R): T1 => R = {
    val cache = new CacheN[Tuple1[T1], R](f.getClass.getName, name, modificationTracker)
    v1 => cache(Tuple1(v1)) { f(v1) }
  }

  def cached[T1, T2, R](name: String, modificationTracker: => ModificationTracker, f: (T1, T2) => R): (T1, T2) => R = {
    val cache = new CacheN[(T1, T2), R](f.getClass.getName, name, modificationTracker)
    (v1, v2) => cache((v1, v2)) { f(v1, v2) }
  }

  def cached[T1, T2, T3, R](name: String, modificationTracker: => ModificationTracker, f: (T1, T2, T3) => R): (T1, T2, T3) => R = {
    val cache = new CacheN[(T1, T2, T3), R](f.getClass.getName, name, modificationTracker)
    (v1, v2, v3) => cache((v1, v2, v3)) { f(v1, v2, v3) }
  }

  def cached[T1, T2, T3, T4, R](name: String, modificationTracker: => ModificationTracker, f: (T1, T2, T3, T4) => R): (T1, T2, T3, T4) => R = {
    val cache = new CacheN[(T1, T2, T3, T4), R](f.getClass.getName, name, modificationTracker)
    (v1, v2, v3, v4) => cache((v1, v2, v3, v4)) { f(v1, v2, v3, v4) }
  }

  def cachedWithoutModificationCount[R](name: String, wrapper: ValueWrapper[R], cleanupScheduler: CleanupScheduler, f: () => R): () => R = {
    val cache = new CacheWithoutModificationCount0[R](f.getClass.getName, name, wrapper, cleanupScheduler)
    () => cache { f() }
  }

  def cachedWithoutModificationCount[T1, R](name: String, wrapper: ValueWrapper[R], cleanupScheduler: CleanupScheduler, f: T1 => R): T1 => R = {
    val cache = new CacheWithoutModificationCountN[Tuple1[T1], R](f.getClass.getName, name, wrapper, cleanupScheduler)
    v1 => cache(Tuple1(v1)) { f(v1) }
  }

  def cachedWithoutModificationCount[T1, T2, R](name: String, wrapper: ValueWrapper[R], cleanupScheduler: CleanupScheduler, f: (T1, T2) => R): (T1, T2) => R = {
    val cache = new CacheWithoutModificationCountN[(T1, T2), R](f.getClass.getName, name, wrapper, cleanupScheduler)
    (v1, v2) => cache((v1, v2)) { f(v1, v2) }
  }

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

  def measure[R](name: String)(f: => R): R = {
    val tracer = Tracer(f.getClass.getName, name)
    tracer.invocation()
    tracer.calculationStart()
    try {
      f
    } finally {
      tracer.calculationEnd()
    }
  }
}

