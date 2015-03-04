package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement

import scala.collection.immutable.HashMap

/**
 * @author Alexander Podkhalyuzin
 */
object ScalaRecursionManager {
  val functionRecursionGuard = RecursionManager.createGuard("function.inference.recursion")
  val resolveReferenceRecursionGuard = RecursionManager.createGuard("resolve.reference.recursion")

  val IMPLICIT_PARAM_TYPES_KEY = "implicit.param.types.key"
  val CYCLIC_HELPER_KEY = "cyclic.helper.key"

  type RecursionMap = Map[(PsiElement, String), List[Object]]
  val recursionMap: ThreadLocal[RecursionMap] =
    new ThreadLocal[RecursionMap] {
      override def initialValue(): RecursionMap =
        new HashMap[(PsiElement, String), List[Object]]
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

  private def getSearches[Dom <: PsiElement](element: Dom, key: String): List[Object] = {
    recursionMap.get().get((element, key)) match {
      case Some(buffer: List[Object]) => buffer
      case _ => List.empty
    }
  }

  private def addLast[Dom <: PsiElement](element: Dom, key: String, obj: Object) {
    recursionMap.get().get((element, key)) match {
      case Some(list) =>
        recursionMap.set(recursionMap.get().updated((element, key), obj :: list))
      case _ =>
        recursionMap.set(recursionMap.get() + ((element, key) -> List(obj)))
    }
  }

  private def removeLast[Dom <: PsiElement](element: Dom, key: String) {
    recursionMap.get().get((element, key)) match {
      case Some(list) =>
        list match {
          case hd :: tl => recursionMap.set(recursionMap.get().updated((element, key), tl))
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
  def doComputations[Dom <: PsiElement, Result](element: Dom, checkAdd: (Object, Seq[Object]) => Boolean,
                                                addElement: Object,
                                                compute: => Result, key: String): Option[Result] = {
    val searches: List[Object] = getSearches(element, key)
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

  def doComputationsForTwoElements[Dom <: PsiElement, Result](element1: Dom, element2: Dom,
                                                              checkAdd: (Object, Seq[Object]) => Boolean,
                                                              addElement1: Object, addElement2: Object,
                                                              compute: => Result, key: String): Option[Result] = {
    val searches1: List[Object] = getSearches(element1, key)
    val searches2: List[Object] = getSearches(element2, key)
    if (checkAdd(addElement1, searches1) && checkAdd(addElement2, searches2)) {
      try {
        addLast(element1, key, addElement1)
        addLast(element2, key, addElement2)

        //computations
        Some(compute)
      }
      finally {
        removeLast(element2, key)
        removeLast(element1, key)
      }
    }
    else None
  }

}