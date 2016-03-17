package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.{Computable, RecursionManager}
import com.intellij.util.containers.ConcurrentWeakHashMap
import org.jetbrains.plugins.scala.lang.psi.types._

/**
  * @author adkozlov
  */
trait Equivalence extends TypeSystemOwner {
  private val guard = RecursionManager.createGuard(s"${typeSystem.name}.equivalence.guard")

  private val cache: ConcurrentWeakHashMap[(ScType, ScType, Boolean), (Boolean, ScUndefinedSubstitutor)] =
    new ConcurrentWeakHashMap[(ScType, ScType, Boolean), (Boolean, ScUndefinedSubstitutor)]()

  private val eval = new ThreadLocal[Boolean] {
    override def initialValue(): Boolean = false
  }

  final def equiv(left: ScType, right: ScType) = equivInner(left, right)._1

  final def clearCache() = cache.clear()

  /**
    * @param falseUndef use false to consider undef type equals to any type
    */
  final def equivInner(left: ScType, right: ScType,
                       substitutor: ScUndefinedSubstitutor = new ScUndefinedSubstitutor,
                       falseUndef: Boolean = true): (Boolean, ScUndefinedSubstitutor) = {
    ProgressManager.checkCanceled()

    if (left == right) return (true, substitutor)

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

    if (guard.currentStack().contains(key)) {
      return (false, new ScUndefinedSubstitutor())
    }

    val result = guard.doPreventingRecursion(key, false, computable(left, right, substitutor, falseUndef))
    if (result == null) return (false, new ScUndefinedSubstitutor())
    if (!nowEval) {
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

  protected def computable(left: ScType, right: ScType, substitutor: ScUndefinedSubstitutor,
                           falseUndef: Boolean): Computable[(Boolean, ScUndefinedSubstitutor)]
}
