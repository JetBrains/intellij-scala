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
                          constraints: ConstraintSystem = ConstraintSystem.empty,
                          checkWeak: Boolean = false): ConstraintsResult = {
    ProgressManager.checkCanceled()

    if (left.isAny || right.isNothing || left == right) return constraints

    if (!right.canBeSameOrInheritor(left)) return ConstraintsResult.Left

    val key = (left, right, checkWeak)

    val fromCache = cache.get(key)
    if (fromCache != null) {
      return fromCache.combine(constraints)
    }
    if (guard.checkReentrancy(key)) {
      return ConstraintsResult.Left
    }

    val stackStamp = RecursionManager.markStack()

    val res = guard.doPreventingRecursion(key, conformsComputable(left, right, visited, checkWeak))
    if (res == null) return ConstraintsResult.Left

    if (stackStamp.mayCacheNow()) {
      cache.put(key, res)
    }
    res.combine(constraints)
  }

  def clearCache(): Unit = cache.clear()

  protected def conformsComputable(left: ScType, right: ScType,
                                   visited: Set[PsiClass],
                                   checkWeak: Boolean): Computable[ConstraintsResult]
}
