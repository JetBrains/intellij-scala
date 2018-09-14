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

  private val guard = RecursionManager.RecursionGuard[Data, ConstraintsResult](s"${typeSystem.name}.conformance.guard")

  private val cache: ConcurrentMap[(ScType, ScType, Boolean), ConstraintsResult] =
    ContainerUtil.newConcurrentMap[(ScType, ScType, Boolean), ConstraintsResult]()

  /**
    * Checks, whether the following assignment is correct:
    * val x: l = (y: r)
    */
  final def conformsInner(left: ScType, right: ScType,
                          visited: Set[PsiClass] = Set.empty,
                          substitutor: ScUndefinedSubstitutor = ScUndefinedSubstitutor(),
                          checkWeak: Boolean = false): ConstraintsResult = {
    ProgressManager.checkCanceled()

    if (left.isAny || right.isNothing || left == right) return substitutor

    if (!right.canBeSameOrInheritor(left)) return ConstraintsResult.Failure

    val key = (left, right, checkWeak)

    val fromCache = cache.get(key)
    if (fromCache != null) {
      return fromCache.combine(substitutor)
    }
    if (guard.checkReentrancy(key)) {
      return ConstraintsResult.Failure
    }

    val stackStamp = RecursionManager.markStack()

    val res = guard.doPreventingRecursion(key, conformsComputable(left, right, visited, checkWeak))
    if (res == null) return ConstraintsResult.Failure

    if (stackStamp.mayCacheNow()) {
      cache.put(key, res)
    }
    res.combine(substitutor)
  }

  def clearCache(): Unit = cache.clear()

  protected def conformsComputable(left: ScType, right: ScType,
                                   visited: Set[PsiClass],
                                   checkWeak: Boolean): Computable[ConstraintsResult]
}
