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
  private type Result = (Boolean, ScUndefinedSubstitutor)

  private val guard = RecursionManager.RecursionGuard[Data, Result](s"${typeSystem.name}.equivalence.guard")

  private val cache: ConcurrentMap[(ScType, ScType, Boolean), (Boolean, ScUndefinedSubstitutor)] =
    ContainerUtil.createConcurrentWeakMap[(ScType, ScType, Boolean), (Boolean, ScUndefinedSubstitutor)]()

  private val eval = new ThreadLocal[Boolean] {
    override def initialValue(): Boolean = false
  }

  final def equiv(left: ScType, right: ScType): Boolean = equivInner(left, right)._1

  def clearCache(): Unit = cache.clear()

  /**
    * @param falseUndef use false to consider undef type equals to any type
    */
  final def equivInner(left: ScType, right: ScType,
                       substitutor: ScUndefinedSubstitutor = ScUndefinedSubstitutor(),
                       falseUndef: Boolean = true): (Boolean, ScUndefinedSubstitutor) = {
    ProgressManager.checkCanceled()

    if (left == right) return (true, substitutor)

    if (!left.canBeSameClass(right)) return (false, substitutor)

    val key = (left, right, falseUndef)

    val nowEval = eval.get()
    val tuple = if (nowEval) null
    else {
      try {
        eval.set(true)
        cache.get(key)
      } finally {
        eval.set(false)
      }
    }
    if (tuple != null) {
      if (substitutor.isEmpty) return tuple
      return tuple.copy(_2 = substitutor + tuple._2)
    }

    if (guard.checkReentrancy(key)) {
      return (false, ScUndefinedSubstitutor())
    }

    val stackStamp = RecursionManager.markStack()

    val result = guard.doPreventingRecursion(key, equivComputable(left, right, ScUndefinedSubstitutor(), falseUndef))

    if (result == null) return (false, ScUndefinedSubstitutor())
    if (!nowEval && stackStamp.mayCacheNow()) {
      try {
        eval.set(true)
        cache.put(key, result)
      } finally {
        eval.set(false)
      }
    }
    if (substitutor.isEmpty) return result
    result.copy(_2 = substitutor + result._2)
  }

  protected def equivComputable(left: ScType, right: ScType,
                                substitutor: ScUndefinedSubstitutor,
                                falseUndef: Boolean): Computable[(Boolean, ScUndefinedSubstitutor)]
}
