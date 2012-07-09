package org.jetbrains.plugins.scala.caches

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.psi.{PsiNamedElement, PsiElement}
import com.intellij.openapi.util.{RecursionManager, Key}
import java.lang.ThreadLocal
import collection.mutable.{ArrayBuffer, HashMap, Map}

/**
 * @author Alexander Podkhalyuzin
 */
object ScalaRecursionManager {
  val functionRecursionGuard = RecursionManager.createGuard("function.inference.recursion")
  val resolveReferenceRecursionGuard = RecursionManager.createGuard("resolve.reference.recursion")

  val IMPLICIT_PARAM_TYPES_KEY = "implicit.param.types.key"
  val CYCLIC_HELPER_KEY = "cyclic.helper.key"

  val recursionMap: ThreadLocal[Map[(PsiElement, String), ArrayBuffer[Object]]] =
    new ThreadLocal[Map[(PsiElement, String), ArrayBuffer[Object]]] {
      override def initialValue(): Map[(PsiElement, String), ArrayBuffer[Object]] =
        new HashMap[(PsiElement, String), ArrayBuffer[Object]]
    }

  private def getSearches[Dom <: PsiElement](element: Dom, key: String): ArrayBuffer[Object] = {
    recursionMap.get().get((element, key)) match {
      case Some(buffer: ArrayBuffer[Object]) => buffer
      case _ =>
        val emptyBuffer = ArrayBuffer.empty[Object]
        recursionMap.get().put((element, key), emptyBuffer)
        emptyBuffer
    }
  }

  private def addLast[Dom <: PsiElement](element: Dom, key: String, obj: Object) {
    recursionMap.get().get((element, key)) match {
      case Some(buffer) => buffer += obj
      case _ => throw new RuntimeException("Match is not exhaustive")
    }
  }

  private def removeLast[Dom <: PsiElement](element: Dom, key: String) {
    recursionMap.get().get((element, key)) match {
      case Some(buffer) => buffer.remove(buffer.length - 1)
      case _ => throw new RuntimeException("Match is not exhaustive")
    }
  }

  /**
   * Do compatations stopping infinite recursion.
   * @param element store information about recursion stack
   * @param checkAdd checks if element is recursive to elements from stack
   * @param addElement element, which to add to recursion stack
   * @param compute computations body
   * @param key to store information about recursion stack
   */
  def doComputations[Dom <: PsiElement, Result](element: Dom, checkAdd: (Object, ArrayBuffer[Object]) => Boolean,
                                                addElement: Object,
                                                compute: => Result, key: String): Option[Result] = {
    val searches: ArrayBuffer[Object] = getSearches(element, key)
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
                                                              checkAdd: (Object, ArrayBuffer[Object]) => Boolean,
                                                              addElement1: Object, addElement2: Object,
                                                              compute: => Result, key: String): Option[Result] = {
    val searches1: ArrayBuffer[Object] = getSearches(element1, key)
    val searches2: ArrayBuffer[Object] = getSearches(element2, key)
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