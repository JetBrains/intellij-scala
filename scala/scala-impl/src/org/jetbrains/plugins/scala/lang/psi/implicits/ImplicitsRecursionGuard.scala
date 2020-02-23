package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType

import scala.collection.immutable.HashMap

/**
 * @author Alexander Podkhalyuzin
 */
object ImplicitsRecursionGuard {

  type RecursionMap = Map[PsiElement, List[ScType]]
  private val recursionMap: ThreadLocal[RecursionMap] =
    new ThreadLocal[RecursionMap] {
      override def initialValue(): RecursionMap =
        new HashMap[PsiElement, List[ScType]]
    }

  def currentMap: RecursionMap = recursionMap.get()

  def setRecursionMap(map: Map[PsiElement, List[ScType]]): Unit = recursionMap.set(map)

  def isRecursive(element: PsiElement, tp: ScType, checkRecursive: (ScType, Seq[ScType]) => Boolean): Boolean =
    checkRecursive(tp, getSearches(element))

  def beforeComputation(element: PsiElement, tp: ScType): Unit = addLast(element, tp)

  def afterComputation(element: PsiElement): Unit = removeLast(element)

  private def getSearches(element: PsiElement): List[ScType] = {
    recursionMap.get().get(element) match {
      case Some(buffer) => buffer
      case _ => List.empty
    }
  }

  private def addLast(element: PsiElement, tp: ScType): Unit = {
    recursionMap.get().get(element) match {
      case Some(list) =>
        recursionMap.set(recursionMap.get().updated(element, tp :: list))
      case _ =>
        recursionMap.set(recursionMap.get() + (element -> List(tp)))
    }
  }

  private def removeLast(element: PsiElement): Unit = {
    recursionMap.get().get(element) match {
      case Some(list) =>
        list match {
          case _ :: tl => recursionMap.set(recursionMap.get().updated(element, tl))
          case _ => recursionMap.set(recursionMap.get() - element)
        }
      case _ => throw new RuntimeException("Match is not exhaustive")
    }
  }
}
