package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.Tracing
import org.jetbrains.plugins.scala.caches.RecursionManager
import org.jetbrains.plugins.scala.caches.stats.{CacheCapabilities, CacheTracker, Tracer}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.types.api.Conformance._
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, Context, ContextDependent, ScType}

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

trait Conformance {
  typeSystem: TypeSystem =>

  import TypeSystem._
  import org.jetbrains.plugins.scala.lang.psi.types.ConstraintsResult.Left

  private val guard = RecursionManager.RecursionGuard[Key, ConstraintsResult](s"${typeSystem.name}.conformance.guard")

  private val cache =
    CacheTracker.alwaysTrack(conformsInnerCache, conformsInnerCache)(
      new ConcurrentHashMap[Key, ContextDependent[ConstraintsResult]]())

  /**
    * Checks, whether the following assignment is correct:
    * val x: l = (y: r)
    */
  final def conformsInner(left: ScType, right: ScType,
                          visited: Set[PsiClass] = Set.empty,
                          constraints: ConstraintSystem = ConstraintSystem.empty,
                          checkWeak: Boolean = false)(implicit context: Context): ConstraintsResult = {
    ProgressManager.checkCanceled()

    if (left.isAny || left.isAnyKind || left.is[WildcardType] || right.isNothing || left == right)
      constraints
    else if (right.canBeSameOrInheritor(left)) {
      val result = conformsInner(Key(left, right, checkWeak), visited)
      combine(result)(constraints)
    } else Left
  }

  def clearCache(): Unit = cache.clear()

  protected def conformsComputable(key: Key, visited: Set[PsiClass])(implicit context: Context): Supplier[ConstraintsResult]

  def conformsInner(key: Key, visited: Set[PsiClass])(implicit context: Context): ConstraintsResult = {
    val tracer = Tracer(conformsInnerCache, conformsInnerCache)
    tracer.invocation()

    val resultInContext = Option(cache.get(key)).getOrElse(new ContextDependent())

    val result = resultInContext.get.orElse {
      guard.doPreventingRecursion(key) {
        val stackStamp = RecursionManager.markStack()
        tracer.calculationStart()
        try {
          val (value, valueInContext) = resultInContext.updatedUsing(ctx => conformsComputable(key, visited)(ctx).get())
          Tracing.conformance(key.left, key.right, value)
          if (stackStamp.mayCacheNow()) {
            cache.put(key, valueInContext)
          }
          value
        } finally {
          tracer.calculationEnd()
        }
      }
    }

    result.getOrElse(Left)
  }
}

private object Conformance {
  private val conformsInnerCache: String = "Conformance.conformsInner"

  private implicit def ConformanceCacheCapabilities[T]: CacheCapabilities[ConcurrentHashMap[T, ContextDependent[ConstraintsResult]]] =
    new CacheCapabilities[ConcurrentHashMap[T, ContextDependent[ConstraintsResult]]] {
      override def cachedEntitiesCount(cache: CacheType): Int = cache.size()
      override def clear(cache: CacheType): Unit = cache.clear()
    }
}
