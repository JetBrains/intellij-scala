package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.scala.caches.RecursionManager
import org.jetbrains.plugins.scala.caches.stats.{CacheCapabilities, CacheTracker, Tracer}
import org.jetbrains.plugins.scala.extensions.NullSafe
import org.jetbrains.plugins.scala.lang.psi.types.api.Equivalence._
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, ScType}

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import scala.util.DynamicVariable

trait Equivalence {
  typeSystem: TypeSystem =>

  import ConstraintsResult.Left
  import TypeSystem._

  private val guard = RecursionManager.RecursionGuard[Key, ConstraintsResult](s"${typeSystem.name}.equivalence.guard")

  private val cache = {
    val cache = new ConcurrentHashMap[Key, ConstraintsResult]()
    CacheTracker.alwaysTrack(equivInnerTraceId, equivInnerTraceId)(this: Equivalence)
    cache
  }

  private val eval = new DynamicVariable(false)

  final def equiv(left: ScType, right: ScType): Boolean = equivInner(left, right).isRight

  def clearCache(): Unit = cache.clear()

  /**
    * @param falseUndef use false to consider undef type equals to any type
    */
  final def equivInner(left: ScType, right: ScType,
                       constraints: ConstraintSystem = ConstraintSystem.empty,
                       falseUndef: Boolean = true): ConstraintsResult = {
    ProgressManager.checkCanceled()

    if (left == right) constraints
    else if (left.canBeSameClass(right)) {
      val result = equivInner(Key(left, right, falseUndef))
      combine(result)(constraints)
    } else Left
  }

  protected def equivComputable(key: Key): Supplier[ConstraintsResult]

  private def equivInner(key: Key): ConstraintsResult = {
    val tracer = Tracer(equivInnerTraceId, equivInnerTraceId)

    tracer.invocation()
    val nowEval = eval.value
    val fromCache =
      if (nowEval) NullSafe.empty
      else eval.withValue(true) {
        NullSafe(cache.get(key))
      }

    fromCache.orElse(
      guard.doPreventingRecursion(key) {
        val stackStamp = RecursionManager.markStack()

        tracer.calculationStart()
        val result = try {
          NullSafe(equivComputable(key).get())
        } finally {
          tracer.calculationEnd()
        }

        result.foreach(result =>
          if (!nowEval && stackStamp.mayCacheNow())
            eval.withValue(true) { cache.put(key, result) }
        )
        result
      }.getOrElse(NullSafe.empty)
    ).getOrElse(Left)
  }
}

object Equivalence {
  val equivInnerTraceId: String = "Equivalence.equivInner"

  implicit val EquivInnerCacheCapabilities: CacheCapabilities[Equivalence] =
    new CacheCapabilities[Equivalence] {
      override def cachedEntitiesCount(cache: CacheType): Int = cache.cache.size()
      override def clear(cache: CacheType): Unit = cache.clearCache()
    }
}