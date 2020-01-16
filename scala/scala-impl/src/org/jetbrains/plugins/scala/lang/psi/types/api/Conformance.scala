package org.jetbrains.plugins.scala.lang.psi
package types
package api

import java.util.concurrent.ConcurrentMap

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiClass
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.caches.RecursionManager
import org.jetbrains.plugins.scala.caches.stats.{CacheCapabilities, CacheTracker, Tracer}
import org.jetbrains.plugins.scala.extensions.NullSafe
import org.jetbrains.plugins.scala.lang.psi.types.api.Conformance._

/**
  * @author adkozlov
  */
trait Conformance {
  typeSystem: TypeSystem =>

  import ConstraintsResult.Left
  import TypeSystem._

  private val guard = RecursionManager.RecursionGuard[Key, ConstraintsResult](s"${typeSystem.name}.conformance.guard")

  private val cache =
    CacheTracker.alwaysTrack(conformsInnerCache, conformsInnerCache) {
      ContainerUtil.newConcurrentMap[Key, ConstraintsResult]()
    }

  /**
    * Checks, whether the following assignment is correct:
    * val x: l = (y: r)
    */
  final def conformsInner(left: ScType, right: ScType,
                          visited: Set[PsiClass] = Set.empty,
                          constraints: ConstraintSystem = ConstraintSystem.empty,
                          checkWeak: Boolean = false): ConstraintsResult = {
    ProgressManager.checkCanceled()

    if (left.isAny || right.isNothing || left == right) constraints
    else if (right.canBeSameOrInheritor(left)) {
      val result = conformsInner(Key(left, right, checkWeak), visited)
      combine(result)(constraints)
    } else Left
  }

  def clearCache(): Unit = cache.clear()

  protected def conformsComputable(key: Key, visited: Set[PsiClass]): Computable[ConstraintsResult]

  def conformsInner(key: Key, visited: Set[PsiClass]): ConstraintsResult = {
    val tracer = Tracer(conformsInnerCache, conformsInnerCache)
    tracer.invocation()

    NullSafe(cache.get(key)).orElse(
      guard.doPreventingRecursion(key) {
        val stackStamp = RecursionManager.markStack()
        tracer.calculationStart()
        try {
          val result = NullSafe(conformsComputable(key, visited).compute())
          result.foreach(result =>
              if (stackStamp.mayCacheNow())
                cache.put(key, result)
          )
          result
        }
        finally {
          tracer.calculationEnd()
        }
      }.getOrElse(NullSafe.empty)
    ).getOrElse(Left)
  }
}

object Conformance {
  val conformsInnerCache: String = "Conformance.conformsInner"
  implicit def ConformanceCacheCapabilities[T]: CacheCapabilities[ConcurrentMap[T, ConstraintsResult]] =
    new CacheCapabilities[ConcurrentMap[T, ConstraintsResult]] {
      override def cachedEntitiesCount(cache: CacheType): Int = cache.size()
      override def clear(cache: CacheType): Unit = cache.clear()
    }
}