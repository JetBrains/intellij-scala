package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.scala.Tracing
import org.jetbrains.plugins.scala.caches.RecursionManager
import org.jetbrains.plugins.scala.caches.stats.{CacheCapabilities, CacheTracker, Tracer}
import org.jetbrains.plugins.scala.lang.psi.types.api.Equivalence._
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, Context, ContextDependent, ScType}

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import scala.util.DynamicVariable

trait Equivalence {
  typeSystem: TypeSystem =>

  import ConstraintsResult.Left
  import TypeSystem._

  private val guard = RecursionManager.RecursionGuard[Key, ConstraintsResult](s"${typeSystem.name}.equivalence.guard")

  private val cache = {
    val cache = new ConcurrentHashMap[Key, ContextDependent[ConstraintsResult]]()
    CacheTracker.alwaysTrack(equivInnerTraceId, equivInnerTraceId)(this: Equivalence)
    cache
  }

  private val eval = new DynamicVariable(false)

  final def equiv(left: ScType, right: ScType)(implicit context: Context): Boolean = equivInner(left, right).isRight

  def clearCache(): Unit = cache.clear()

  /**
    * @param falseUndef use false to consider undef type equals to any type
    */
  final def equivInner(left: ScType, right: ScType,
                       constraints: ConstraintSystem = ConstraintSystem.empty,
                       falseUndef: Boolean = true)(implicit context: Context): ConstraintsResult = {
    ProgressManager.checkCanceled()

    if (left == right) constraints
    else if (left.canBeSameClass(right)) {
      val result = equivInner(Key(left, right, falseUndef))
      combine(result)(constraints)
    } else Left
  }

  protected def equivComputable(key: Key)(implicit context: Context): Supplier[ConstraintsResult]

  private def equivInner(key: Key)(implicit context: Context): ConstraintsResult = {
    val tracer = Tracer(equivInnerTraceId, equivInnerTraceId)
    tracer.invocation()

    val nowEval = eval.value

    val resultInContext =
      if (nowEval) new ContextDependent[ConstraintsResult]()
      else eval.withValue(true)(Option(cache.get(key)).getOrElse(new ContextDependent()))

    val result = resultInContext.get.orElse {
      guard.doPreventingRecursion(key) {
        val stackStamp = RecursionManager.markStack()

        tracer.calculationStart()
        try {
          val (value, valueInContext) = resultInContext.updatedUsing(ctx => equivComputable(key)(ctx).get())
          Tracing.equivalence(key.left, key.right, value)
          if (!nowEval && stackStamp.mayCacheNow()) {
            eval.withValue(true) {
              cache.put(key, valueInContext)
            }
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

private object Equivalence {
  private val equivInnerTraceId: String = "Equivalence.equivInner"

  private implicit val EquivInnerCacheCapabilities: CacheCapabilities[Equivalence] =
    new CacheCapabilities[Equivalence] {
      override def cachedEntitiesCount(cache: CacheType): Int = cache.cache.size()
      override def clear(cache: CacheType): Unit = cache.clearCache()
    }
}