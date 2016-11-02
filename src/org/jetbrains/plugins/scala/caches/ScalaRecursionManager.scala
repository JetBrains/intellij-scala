package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType

import scala.collection.immutable.HashMap

/**
 * @author Alexander Podkhalyuzin
 */
object ScalaRecursionManager {
  val functionRecursionGuard = RecursionManager.createGuard("function.inference.recursion")
  val resolveReferenceRecursionGuard = RecursionManager.createGuard("resolve.reference.recursion")

  val IMPLICIT_PARAM_TYPES_KEY = "implicit.param.types.key"

  type RecursionMap = Map[(PsiElement, String), List[ScType]]
  val recursionMap: ThreadLocal[RecursionMap] =
    new ThreadLocal[RecursionMap] {
      override def initialValue(): RecursionMap =
        new HashMap[(PsiElement, String), List[ScType]]
    }

  def usingPreviousRecursionMap[T](m: RecursionMap)(body: => T): T = {
    val currentMap = recursionMap.get()
    try {
      recursionMap.set(m)
      body
    } finally {
      recursionMap.set(currentMap)
    }
  }

  private def getSearches[Dom <: PsiElement, Recursive <: AnyRef](element: Dom, key: String): List[Recursive] = {
    recursionMap.get().get((element, key)) match {
      case Some(buffer: List[Recursive]) => buffer
      case _ => List.empty
    }
  }

  private def addLast[Dom <: PsiElement](element: Dom, key: String, tp: ScType) {
    recursionMap.get().get((element, key)) match {
      case Some(list) =>
        recursionMap.set(recursionMap.get().updated((element, key), tp :: list))
      case _ =>
        recursionMap.set(recursionMap.get() + ((element, key) -> List(tp)))
    }
  }

  private def removeLast[Dom <: PsiElement](element: Dom, key: String) {
    recursionMap.get().get((element, key)) match {
      case Some(list) =>
        list match {
          case _ :: tl => recursionMap.set(recursionMap.get().updated((element, key), tl))
          case _ => recursionMap.set(recursionMap.get() - ((element, key)))
        }
      case _ => throw new RuntimeException("Match is not exhaustive")
    }
  }

  /**
   * Do computations stopping infinite recursion.
   * @param element store information about recursion stack
   * @param checkAdd checks if element is recursive to elements from stack
   * @param addElement element, which to add to recursion stack
   * @param compute computations body
   * @param key to store information about recursion stack
   */
  def doComputations[Dom <: PsiElement, Result](element: Dom, checkAdd: (ScType, Seq[ScType]) => Boolean,
                                                addElement: ScType,
                                                compute: => Result, key: String): Option[Result] = {
    val searches: List[ScType] = getSearches(element, key)
    if (checkAdd(addElement, searches)) {
      try {
        addLast(element, key, addElement)

        //computations
        Some(compute)
      }
      finally {
        removeLast(element, key)
      }
    }
    else None
  }
}
