package org.jetbrains.plugins.scala.lang.psi.types.api

import java.util.concurrent.ConcurrentMap

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiClass
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.caches.RecursionManager
import org.jetbrains.plugins.scala.lang.psi.types._


/**
  * @author adkozlov
  */
trait Conformance {
  typeSystem: TypeSystem =>

  private type Data = (ScType, ScType, Boolean)
  private type Result = (Boolean, ScUndefinedSubstitutor)

  private val guard = RecursionManager.RecursionGuard[Data, Result](s"${typeSystem.name}.conformance.guard")

  private val cache: ConcurrentMap[(ScType, ScType, Boolean), (Boolean, ScUndefinedSubstitutor)] =
    ContainerUtil.createConcurrentWeakMap[(ScType, ScType, Boolean), (Boolean, ScUndefinedSubstitutor)]()

  /**
    * Checks, whether the following assignment is correct:
    * val x: l = (y: r)
    */
  final def conformsInner(left: ScType, right: ScType,
                          visited: Set[PsiClass] = Set.empty,
                          substitutor: ScUndefinedSubstitutor = ScUndefinedSubstitutor(),
                          checkWeak: Boolean = false): Result = {
    ProgressManager.checkCanceled()

    if (left.isAny || right.isNothing || left == right) return (true, substitutor)

    if (!right.canBeSameOrInheritor(left)) return (false, substitutor)

    val key = (left, right, checkWeak)

    val tuple = cache.get(key)
    if (tuple != null) {
      if (substitutor.isEmpty) return tuple
      return tuple.copy(_2 = substitutor + tuple._2)
    }
    if (guard.checkReentrancy(key)) {
      return (false, ScUndefinedSubstitutor())
    }

    val stackStamp = RecursionManager.markStack()

    val res = guard.doPreventingRecursion(key, conformsComputable(left, right, visited, checkWeak))
    if (res == null) return (false, ScUndefinedSubstitutor())

    if (stackStamp.mayCacheNow()) {
      cache.put(key, res)
    }

    if (substitutor.isEmpty) return res
    res.copy(_2 = substitutor + res._2)
  }

  def clearCache(): Unit = cache.clear()

  protected def conformsComputable(left: ScType, right: ScType,
                                   visited: Set[PsiClass],
                                   checkWeak: Boolean): Computable[(Boolean, ScUndefinedSubstitutor)]
}
