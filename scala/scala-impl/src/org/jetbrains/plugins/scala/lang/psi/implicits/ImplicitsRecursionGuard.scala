package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.util.UnloadableThreadLocal

import scala.collection.immutable.HashMap

object ImplicitsRecursionGuard {

  type RecursionMap = Map[PsiElement, List[ScType]]
  private val recursionMap: UnloadableThreadLocal[RecursionMap] =
    UnloadableThreadLocal(new HashMap[PsiElement, List[ScType]])

  def currentMap: RecursionMap = recursionMap.value

  def setRecursionMap(map: Map[PsiElement, List[ScType]]): Unit =
    recursionMap.value = map

  def isRecursive(element: PsiElement, tp: ScType, checkRecursive: (ScType, Seq[ScType]) => Boolean): Boolean =
    checkRecursive(tp, getSearches(element))

  def beforeComputation(element: PsiElement, tp: ScType): Unit = addLast(element, tp)

  def afterComputation(element: PsiElement): Unit = removeLast(element)

  private def getSearches(element: PsiElement): List[ScType] = {
    recursionMap.value.get(element) match {
      case Some(buffer) => buffer
      case _ => List.empty
    }
  }

  private def addLast(element: PsiElement, tp: ScType): Unit = {
    val rmap = recursionMap.value
    recursionMap.value =
      rmap.get(element) match {
        case Some(list) =>
          rmap.updated(element, tp :: list)
        case _ =>
          rmap + (element -> List(tp))
      }
  }

  private def removeLast(element: PsiElement): Unit = {
    val rmap = recursionMap.value
    rmap.get(element) match {
      case Some(list) =>
        recursionMap.value =
          //empty lists should not be stored in the map
          list match {
            case _ :: Nil => rmap - element
            case _ :: tl => rmap.updated(element, tl)
            case _ => ???
          }
      case _ =>
        ???
    }
  }
}
