package org.jetbrains.plugins.scala.lang.psi
package types
package api

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiClass
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.caches.RecursionManager
import org.jetbrains.plugins.scala.caches.stats.CacheStatsCollector

/**
  * @author adkozlov
  */
trait Conformance {
  typeSystem: TypeSystem =>

  import ConstraintsResult.Left
  import TypeSystem._

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

  def conformsInner(key: Key, visited: Set[PsiClass]): ConstraintsResult = {
    val cacheStats = CacheStatsCollector("Conformance.conformsInner", "conformsInner")
    cacheStats.invocation()

    cache.get(key) match {
      case null if guard.checkReentrancy(key) => Left
      case null =>
        val stackStamp = RecursionManager.markStack()
        cacheStats.calculationStart()
        try {
          guard.doPreventingRecursion(key, conformsComputable(key, visited)) match {
            case null => Left
            case result =>
              if (stackStamp.mayCacheNow()) cache.put(key, result)
              result
          }
        }
        finally {
          cacheStats.calculationEnd()
        }
      case cached => cached
    }
  }
}
