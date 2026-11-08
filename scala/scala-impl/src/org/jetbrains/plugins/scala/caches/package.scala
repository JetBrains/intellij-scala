package org.jetbrains.plugins.scala

import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.caches.CacheInUserData._
import org.jetbrains.plugins.scala.caches.CacheWithRecursionGuard.{cacheWithRecursionGuard0, cacheWithRecursionGuardN}
import org.jetbrains.plugins.scala.caches.stats.Tracer

import java.util.concurrent.ConcurrentHashMap

package object caches {

  // TODO Detect control flow exceptions

  def cached[R](name: String, modificationTracker: => ModificationTracker, f: () => R): () => R = {
    val identity = identityFor(f.getClass, name)
    val cache = new Cache0[R](identity.id, identity.name, modificationTracker)
    () => cache { f() }
  }

  def cached[T1, R](name: String, modificationTracker: => ModificationTracker, f: T1 => R): T1 => R = {
    val identity = identityFor(f.getClass, name)
    val cache = new CacheN[Tuple1[T1], R](identity.id, identity.name, modificationTracker)
    v1 => cache(Tuple1(v1)) { f(v1) }
  }

  def cached[T1, T2, R](name: String, modificationTracker: => ModificationTracker, f: (T1, T2) => R): (T1, T2) => R = {
    val identity = identityFor(f.getClass, name)
    val cache = new CacheN[(T1, T2), R](identity.id, identity.name, modificationTracker)
    (v1, v2) => cache((v1, v2)) { f(v1, v2) }
  }

  def cached[T1, T2, T3, R](name: String, modificationTracker: => ModificationTracker, f: (T1, T2, T3) => R): (T1, T2, T3) => R = {
    val identity = identityFor(f.getClass, name)
    val cache = new CacheN[(T1, T2, T3), R](identity.id, identity.name, modificationTracker)
    (v1, v2, v3) => cache((v1, v2, v3)) { f(v1, v2, v3) }
  }

  def cached[T1, T2, T3, T4, R](name: String, modificationTracker: => ModificationTracker, f: (T1, T2, T3, T4) => R): (T1, T2, T3, T4) => R = {
    val identity = identityFor(f.getClass, name)
    val cache = new CacheN[(T1, T2, T3, T4), R](identity.id, identity.name, modificationTracker)
    (v1, v2, v3, v4) => cache((v1, v2, v3, v4)) { f(v1, v2, v3, v4) }
  }

  def cachedWithoutModificationCount[R](name: String, wrapper: ValueWrapper[R], cleanupScheduler: CleanupScheduler, f: () => R): () => R = {
    val identity = identityFor(f.getClass, name)
    val cache = new CacheWithoutModificationCount0[R](identity.id, identity.name, wrapper, cleanupScheduler)
    () => cache { f() }
  }

  def cachedWithoutModificationCount[T1, R](name: String, wrapper: ValueWrapper[R], cleanupScheduler: CleanupScheduler, f: T1 => R): T1 => R = {
    val identity = identityFor(f.getClass, name)
    val cache = new CacheWithoutModificationCountN[Tuple1[T1], R](identity.id, identity.name, wrapper, cleanupScheduler)
    v1 => cache(Tuple1(v1)) { f(v1) }
  }

  def cachedWithoutModificationCount[T1, T2, R](name: String, wrapper: ValueWrapper[R], cleanupScheduler: CleanupScheduler, f: (T1, T2) => R): (T1, T2) => R = {
    val identity = identityFor(f.getClass, name)
    val cache = new CacheWithoutModificationCountN[(T1, T2), R](identity.id, identity.name, wrapper, cleanupScheduler)
    (v1, v2) => cache((v1, v2)) { f(v1, v2) }
  }

  // TODO Factory method instead of the ProjectUserDataHolder type class

  def cachedInUserData[E: ProjectUserDataHolder, R](name: String, dataHolder: E, dependency: => AnyRef)(f: => R): R = {
    val identity = identityFor((() => f).getClass, name)
    cacheInUserData0(identity.id, identity.name, dataHolder, dependency, f)
  }

  def cachedInUserData[E: ProjectUserDataHolder, T <: Product, R](name: String, dataHolder: E, dependency: => AnyRef, v: T)(f: => R): R = {
    val identity = identityFor((() => f).getClass, name)
    cacheInUserDataN[E, T, R](identity.id, identity.name, dataHolder, dependency, v, f)
  }

  // TODO (defaultValue: => R) parameter list

  def cachedWithRecursionGuard[R](name: String, element: PsiElement, defaultValue: => R, dependency: => AnyRef)(f: => R): R = {
    val identity = identityFor((() => f).getClass, name)
    cacheWithRecursionGuard0(identity.id, identity.name, element, defaultValue, dependency, f)
  }

  def cachedWithRecursionGuard[T <: Product, R](name: String, element: PsiElement, defaultValue: => R, dependency: => AnyRef, v: T)(f: => R): R = {
    val identity = identityFor((() => f).getClass, name)
    cacheWithRecursionGuardN[T, R](identity.id, identity.name, element, defaultValue, dependency, v, f)
  }

  def measure[R](name: String)(f: => R): R = {
    val identity = identityFor((() => f).getClass, name)
    val tracer = Tracer(identity.id, identity.name)
    tracer.invocation()
    tracer.calculationStart()
    try {
      f
    } finally {
      tracer.calculationEnd()
    }
  }

  private final case class CacheIdentity(id: String, name: String)

  // The identity depends only on the call site (its lambda class) and the name, but caches are built per PSI element
  // and the cachedInUserData/cachedWithRecursionGuard/measure family asks on every call. Building the strings each
  // time kept one copy per cache instance alive (millions in a large project) and allocated on every call.
  // ClassValue, rather than a map keyed by the class, lets lambda classes unload with the plugin.
  private val identities: ClassValue[ConcurrentHashMap[String, CacheIdentity]] =
    new ClassValue[ConcurrentHashMap[String, CacheIdentity]] {
      override def computeValue(lambdaClass: Class[?]): ConcurrentHashMap[String, CacheIdentity] = new ConcurrentHashMap()
    }

  private def identityFor(lambdaClass: Class[?], name: String): CacheIdentity =
    identities.get(lambdaClass).computeIfAbsent(name, _ => CacheIdentity(idFor(lambdaClass, name), nameFor(lambdaClass, name)))

  private def idFor(lambdaClass: Class[?], name: String): String =
    withoutLambdaSuffix(lambdaClass.getName).replace('.', '$') + "$" + name.replace('.', '$') + "$cacheKey"

  private def nameFor(lambdaClass: Class[?], name: String): String =
    withoutLambdaSuffix(lambdaClass.getSimpleName) + "." + name

  private def withoutLambdaSuffix(name: String): String = {
    val i = name.indexOf("$$Lambda$")
    if (i != -1) name.substring(0, i) else name
  }
}
