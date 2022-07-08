package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.caches.RecursionManager
import org.jetbrains.plugins.scala.caches.stats.{CacheCapabilities, CacheTracker, Tracer}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.types.api.Conformance._
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, ScType}
import org.jetbrains.plugins.scala.traceLogger.TraceLogger

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

trait Conformance {
  typeSystem: TypeSystem =>

  import TypeSystem._
  import org.jetbrains.plugins.scala.lang.psi.types.ConstraintsResult.Left

  private val guard = RecursionManager.RecursionGuard[Key, ConstraintsResult](s"${typeSystem.name}.conformance.guard")

  private val cache =
    CacheTracker.alwaysTrack(conformsInnerCache, conformsInnerCache) {
      new ConcurrentHashMap[Key, ConstraintsResult]()
    }

  /**
    * Checks, whether the following assignment is correct:
    * val x: l = (y: r)
    */
  final def conformsInner(left: ScType, right: ScType,
                          visited: Set[PsiClass] = Set.empty,
                          constraints: ConstraintSystem = ConstraintSystem.empty,
                          checkWeak: Boolean = false): ConstraintsResult = TraceLogger.func {
    ProgressManager.checkCanceled()

    if (left.isAny || left.is[WildcardType] || right.isNothing || left == right) constraints
    else if (right.canBeSameOrInheritor(left)) {
      val result = conformsInner(Key(left, right, checkWeak), visited)
      combine(result)(constraints)
    } else Left
  }

  def clearCache(): Unit = cache.clear()

  protected def conformsComputable(key: Key, visited: Set[PsiClass]): Supplier[ConstraintsResult]

  def conformsInner(key: Key, visited: Set[PsiClass]): ConstraintsResult = {
    val tracer = Tracer(conformsInnerCache, conformsInnerCache)
    tracer.invocation()

    NullSafe(cache.get(key)).orElse(
      guard.doPreventingRecursion(key) {
        val stackStamp = RecursionManager.markStack()
        tracer.calculationStart()
        try {
          val result = NullSafe(conformsComputable(key, visited).get())
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
  implicit def ConformanceCacheCapabilities[T]: CacheCapabilities[ConcurrentHashMap[T, ConstraintsResult]] =
    new CacheCapabilities[ConcurrentHashMap[T, ConstraintsResult]] {
      override def cachedEntitiesCount(cache: CacheType): Int = cache.size()
      override def clear(cache: CacheType): Unit = cache.clear()
    }
}