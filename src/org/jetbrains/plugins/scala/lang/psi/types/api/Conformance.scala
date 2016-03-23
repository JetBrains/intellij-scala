package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.{Computable, RecursionManager}
import com.intellij.psi.PsiClass
import com.intellij.util.containers.ConcurrentWeakHashMap
import org.jetbrains.plugins.scala.lang.psi.types._

import scala.collection.immutable.HashSet

/**
  * @author adkozlov
  */
trait Conformance extends TypeSystemOwner {
  private val guard = RecursionManager.createGuard(s"${typeSystem.name}.conformance.guard")

  private val cache: ConcurrentWeakHashMap[(ScType, ScType, Boolean), (Boolean, ScUndefinedSubstitutor)] =
    new ConcurrentWeakHashMap[(ScType, ScType, Boolean), (Boolean, ScUndefinedSubstitutor)]()

  /**
    * Checks, whether the following assignment is correct:
    * val x: l = (y: r)
    */
  final def conformsInner(left: ScType, right: ScType,
                          visited: Set[PsiClass] = HashSet.empty,
                          substitutor: ScUndefinedSubstitutor = new ScUndefinedSubstitutor(),
                          checkWeak: Boolean = false): (Boolean, ScUndefinedSubstitutor) = {
    ProgressManager.checkCanceled()

    val key = (left, right, checkWeak)

    val tuple = cache.get(key)
    if (tuple != null) {
      if (substitutor.isEmpty) return tuple
      return tuple.copy(_2 = substitutor + tuple._2)
    }
    if (guard.currentStack().contains(key)) {
      return (false, new ScUndefinedSubstitutor())
    }

    val res = guard.doPreventingRecursion(key, false, computable(left, right, visited, checkWeak))
    if (res == null) return (false, new ScUndefinedSubstitutor())
    cache.put(key, res)
    if (substitutor.isEmpty) return res
    res.copy(_2 = substitutor + res._2)
  }

  final def clearCache() = cache.clear()

  protected def computable(left: ScType, right: ScType,
                           visited: Set[PsiClass],
                           checkWeak: Boolean): Computable[(Boolean, ScUndefinedSubstitutor)]
}
