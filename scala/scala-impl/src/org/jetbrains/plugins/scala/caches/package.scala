package org.jetbrains.plugins.scala

import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.caches.CacheInUserData._
import org.jetbrains.plugins.scala.caches.CacheWithRecursionGuard.{cacheWithRecursionGuard0, cacheWithRecursionGuardN}
import org.jetbrains.plugins.scala.caches.stats.Tracer

package object caches {

  // TODO Detect control flow exceptions

  def cached[R](name: String, modificationTracker: => ModificationTracker, f: () => R): () => R = {
    val cache = new Cache0[R](idFor(f.getClass, name), nameFor(f.getClass, name), modificationTracker)
    () => cache { f() }
  }

  def cached[T1, R](name: String, modificationTracker: => ModificationTracker, f: T1 => R): T1 => R = {
    val cache = new CacheN[Tuple1[T1], R](idFor(f.getClass, name), nameFor(f.getClass, name), modificationTracker)
    v1 => cache(Tuple1(v1)) { f(v1) }
  }

  def cached[T1, T2, R](name: String, modificationTracker: => ModificationTracker, f: (T1, T2) => R): (T1, T2) => R = {
    val cache = new CacheN[(T1, T2), R](idFor(f.getClass, name), nameFor(f.getClass, name), modificationTracker)
    (v1, v2) => cache((v1, v2)) { f(v1, v2) }
  }

  def cached[T1, T2, T3, R](name: String, modificationTracker: => ModificationTracker, f: (T1, T2, T3) => R): (T1, T2, T3) => R = {
    val cache = new CacheN[(T1, T2, T3), R](idFor(f.getClass, name), nameFor(f.getClass, name), modificationTracker)
    (v1, v2, v3) => cache((v1, v2, v3)) { f(v1, v2, v3) }
  }

  def cached[T1, T2, T3, T4, R](name: String, modificationTracker: => ModificationTracker, f: (T1, T2, T3, T4) => R): (T1, T2, T3, T4) => R = {
    val cache = new CacheN[(T1, T2, T3, T4), R](idFor(f.getClass, name), nameFor(f.getClass, name), modificationTracker)
    (v1, v2, v3, v4) => cache((v1, v2, v3, v4)) { f(v1, v2, v3, v4) }
  }

  def cachedWithoutModificationCount[R](name: String, wrapper: ValueWrapper[R], cleanupScheduler: CleanupScheduler, f: () => R): () => R = {
    val cache = new CacheWithoutModificationCount0[R](idFor(f.getClass, name), nameFor(f.getClass, name), wrapper, cleanupScheduler)
    () => cache { f() }
  }

  def cachedWithoutModificationCount[T1, R](name: String, wrapper: ValueWrapper[R], cleanupScheduler: CleanupScheduler, f: T1 => R): T1 => R = {
    val cache = new CacheWithoutModificationCountN[Tuple1[T1], R](idFor(f.getClass, name), nameFor(f.getClass, name), wrapper, cleanupScheduler)
    v1 => cache(Tuple1(v1)) { f(v1) }
  }

  def cachedWithoutModificationCount[T1, T2, R](name: String, wrapper: ValueWrapper[R], cleanupScheduler: CleanupScheduler, f: (T1, T2) => R): (T1, T2) => R = {
    val cache = new CacheWithoutModificationCountN[(T1, T2), R](idFor(f.getClass, name), nameFor(f.getClass, name), wrapper, cleanupScheduler)
    (v1, v2) => cache((v1, v2)) { f(v1, v2) }
  }

  // TODO Factory method instead of the ProjectUserDataHolder type class

  def cachedInUserData[E: ProjectUserDataHolder, R](name: String, dataHolder: E, dependency: => AnyRef)(f: => R): R =
    cacheInUserData0(idFor((() => f).getClass, name), nameFor((() => f).getClass, name), dataHolder, dependency, f)

  def cachedInUserData[E: ProjectUserDataHolder, T <: Product, R](name: String, dataHolder: E, dependency: => AnyRef, v: T)(f: => R): R =
    cacheInUserDataN[E, T, R](idFor((() => f).getClass, name), nameFor((() => f).getClass, name), dataHolder, dependency, v, f)

  // TODO (defaultValue: => R) parameter list

  def cachedWithRecursionGuard[R](name: String, element: PsiElement, defaultValue: => R, dependency: => AnyRef)(f: => R): R =
    cacheWithRecursionGuard0(idFor((() => f).getClass, name), nameFor((() => f).getClass, name), element, defaultValue, dependency, f)

  def cachedWithRecursionGuard[T <: Product, R](name: String, element: PsiElement, defaultValue: => R, dependency: => AnyRef, v: T)(f: => R): R =
    cacheWithRecursionGuardN[T, R](idFor((() => f).getClass, name), nameFor((() => f).getClass, name), element, defaultValue, dependency, v, f)

  def measure[R](name: String)(f: => R): R = {
    val tracer = Tracer(idFor((() => f).getClass, name), nameFor((() => f).getClass, name))
    tracer.invocation()
    tracer.calculationStart()
    try {
      f
    } finally {
      tracer.calculationEnd()
    }
  }

  /** Computes an unique cache identifier.
   *
   *  Return human-readable strings only if `caches.stats` are enabled to reduce CPU and RAM use:
   *  * Because the method is called frequently, it's best to avoid string transformations.
   *  * Because results are effectively stored in `Cache` fields, it's best to reference singleton objects rather than unique `String` instances.
   *
   *  @param lambdaClass class of the function that computes values, must be unique
   *  @param name cache name, e.g. "annotation", must be unique within the enclosing class
   *
   *  @return cache identifier, for example:
   *  * `caches.stats` enabled  : `org$example$Foo$$annotation$cacheKey`
   *  * `caches.stats` disabled : `org.example.Foo$$Lambda/0x000000000000001a`
   *
   *  @see https://youtrack.jetbrains.com/issue/SCL-26384#focus=Comments-27-14629331.0-0
   */
  private def idFor(lambdaClass: Class[?], name: String): String =
    if (Tracer.isEnabled) withoutLambdaSuffix(lambdaClass.getName).replace('.', '$') + "$" + name.replace('.', '$') + "$cacheKey"
    else lambdaClass.getName

  /** Computes a qualified cache name.
   *
   *  Return human-readable strings only if `caches.stats` are enabled to reduce CPU and RAM use:
   *  * Because the method is called frequently, it's best to avoid string transformations.
   *  * Because results are effectively stored in `Cache` fields, it's best to reference singleton objects rather than unique `String` instances.
   *
   *  @param lambdaClass class of the function that computes values, must be unique
   *  @param name cache name, e.g. "annotation", must be unique within the enclosing class
   *
   *  @return qualified cache name, for example:
   *  * `caches.stats` enabled  : `Foo.annotation`
   *  * `caches.stats` disabled : `org.example.Foo$$Lambda/0x000000000000001a`
   *
   *  @see https://youtrack.jetbrains.com/issue/SCL-26384#focus=Comments-27-14629331.0-0
   */
  private def nameFor(lambdaClass: Class[?], name: String): String =
    if (Tracer.isEnabled) withoutLambdaSuffix(lambdaClass.getSimpleName) + "." + name
    else lambdaClass.getName

  private def withoutLambdaSuffix(name: String): String = {
    val i = name.indexOf("$$Lambda/") // Since JDK 21, lambda class names are like "org.example.Foo$$Lambda/0x000000000000001a"
    if (i != -1) name.substring(0, i) else name
  }
}

