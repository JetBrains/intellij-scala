package org.jetbrains.plugins.scala.lang.psi
package types
package api

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.caches.RecursionManager

/**
  * @author adkozlov
  */
trait Equivalence {
  typeSystem: TypeSystem =>

  import TypeSystem._
  import ConstraintsResult.Left

  private val guard = RecursionManager.RecursionGuard[Key, ConstraintsResult](s"${typeSystem.name}.equivalence.guard")

  private val cache = ContainerUtil.newConcurrentMap[Key, ConstraintsResult]()

  private val eval = new ThreadLocal[Boolean] {
    override def initialValue(): Boolean = false
  }

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

  protected def equivComputable(key: Key): Computable[ConstraintsResult]

  private def equivInner(key: Key): ConstraintsResult = {
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

    fromCache match {
      case null if guard.checkReentrancy(key) => Left
      case null =>
        val stackStamp = RecursionManager.markStack()

        guard.doPreventingRecursion(key, equivComputable(key)) match {
          case null => Left
          case result =>
            if (!nowEval && stackStamp.mayCacheNow()) {
              try {
                eval.set(true)
                cache.put(key, result)
              } finally {
                eval.set(false)
              }
            }
            result
        }
      case cached => cached
    }
  }
}
