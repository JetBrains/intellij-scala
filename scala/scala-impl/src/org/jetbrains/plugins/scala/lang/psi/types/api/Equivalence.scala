package org.jetbrains.plugins.scala.lang.psi.types.api

import java.util.concurrent.ConcurrentMap

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.caches.RecursionManager
import org.jetbrains.plugins.scala.lang.psi.types._

/**
  * @author adkozlov
  */
trait Equivalence {
  typeSystem: TypeSystem =>

  private type Data = (ScType, ScType, Boolean)

  private val guard = RecursionManager.RecursionGuard[Data, ConstraintsResult](s"${typeSystem.name}.equivalence.guard")

  private val cache: ConcurrentMap[(ScType, ScType, Boolean), ConstraintsResult] =
    ContainerUtil.newConcurrentMap[(ScType, ScType, Boolean), ConstraintsResult]()

  private val eval = new ThreadLocal[Boolean] {
    override def initialValue(): Boolean = false
  }

  final def equiv(left: ScType, right: ScType): Boolean = equivInner(left, right).isSuccess

  def clearCache(): Unit = cache.clear()

  /**
    * @param falseUndef use false to consider undef type equals to any type
    */
  final def equivInner(left: ScType, right: ScType,
                       constraints: ConstraintSystem = ConstraintSystem.empty,
                       falseUndef: Boolean = true): ConstraintsResult = {
    ProgressManager.checkCanceled()

    if (left == right) return constraints

    if (!left.canBeSameClass(right)) return ConstraintsResult.Failure

    val key = (left, right, falseUndef)

    val nowEval = eval.get()
    val fromCache = if (nowEval) null
    else {
      try {
        eval.set(true)
        cache.get(key)
      } finally {
        eval.set(false)
      }
    }
    if (fromCache != null) {
      return fromCache.combine(constraints)
    }

    if (guard.checkReentrancy(key)) {
      return ConstraintsResult.Failure
    }

    val stackStamp = RecursionManager.markStack()

    val result = guard.doPreventingRecursion(key, equivComputable(left, right, ConstraintSystem.empty, falseUndef))

    if (result == null) return ConstraintsResult.Failure

    if (!nowEval && stackStamp.mayCacheNow()) {
      try {
        eval.set(true)
        cache.put(key, result)
      } finally {
        eval.set(false)
      }
    }
    result.combine(constraints)
  }

  protected def equivComputable(left: ScType, right: ScType,
                                constraints: ConstraintSystem,
                                falseUndef: Boolean): Computable[ConstraintsResult]
}
