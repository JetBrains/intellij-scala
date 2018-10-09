package org.jetbrains.plugins.scala.lang.psi
package types
package api

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiClass
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.caches.RecursionManager

/**
  * @author adkozlov
  */
trait Conformance {
  typeSystem: TypeSystem =>

  import TypeSystem._
  import ConstraintsResult.Left

  private val guard = RecursionManager.RecursionGuard[Key, ConstraintsResult](s"${typeSystem.name}.conformance.guard")

  private val cache = ContainerUtil.newConcurrentMap[Key, ConstraintsResult]()

  /**
    * Checks, whether the following assignment is correct:
    * val x: l = (y: r)
    */
  final def conformsInner(left: ScType, right: ScType,
                          visited: Set[PsiClass] = Set.empty,
                          constraints: ConstraintSystem = ConstraintSystem.empty,
                          checkWeak: Boolean = false): ConstraintsResult = {
    ProgressManager.checkCanceled()

    if (left.isAny || right.isNothing || left == right) constraints
    else if (right.canBeSameOrInheritor(left)) {
      val result = conformsInner(Key(left, right, checkWeak), visited)
      combine(result)(constraints)
    } else Left
  }

  def clearCache(): Unit = cache.clear()

  protected def conformsComputable(key: Key, visited: Set[PsiClass]): Computable[ConstraintsResult]

  private def conformsInner(key: Key, visited: Set[PsiClass]) = cache.get(key) match {
    case null if guard.checkReentrancy(key) => Left
    case null =>
      val stackStamp = RecursionManager.markStack()

      guard.doPreventingRecursion(key, conformsComputable(key, visited)) match {
        case null => Left
        case result =>
          if (stackStamp.mayCacheNow()) cache.put(key, result)
          result
      }
    case cached => cached
  }
}
