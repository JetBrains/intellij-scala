package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.util.{Key, ModificationTracker}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.caches.BlockModificationTracker._
import org.jetbrains.plugins.scala.caches.CachesUtil.scalaTopLevelModTracker
import org.jetbrains.plugins.scala.caches.ProjectUserDataHolder._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.annotation.tailrec

class BlockModificationTracker private (element: PsiElement) extends ModificationTracker {

  private val topLevel = scalaTopLevelModTracker(element.getProject)

  def getModificationCount: Long = {
    originalContextsWithStableType(element)
      .foldLeft(topLevel.getModificationCount)(_ + _.modificationCount)
  }
}

object BlockModificationTracker {

  def apply(element: PsiElement): ModificationTracker =
    if (!element.isValid) ModificationTracker.NEVER_CHANGED
    else new BlockModificationTracker(element)

  private val originalPositionKey: Key[PsiElement] = Key.create("original.position.completion.key")

  //in completion we need to compute modification count of elements in "completion file"
  //we need to use original file for that to have up-to-date completion results
  def setOriginalPosition(element: PsiElement, original: PsiElement): Unit = {
    if (original != null) {
      for {
        elementContext  <- contextWithStableType(element)
        originalContext <- contextWithStableType(original)
      } {
        elementContext.putUserDataIfAbsent(originalPositionKey, originalContext)
      }
      //      assert(enclosingModificationOwner(element).getModificationCount == enclosingModificationOwner(original).getModificationCount)
    }
  }

  private def originalPosition(element: PsiElement): PsiElement =
    Option(element)
      .flatMap(e => Option(e.getUserData(originalPositionKey)))
      .getOrElse(element)

  @tailrec
  private def originalContextsWithStableType(element: PsiElement, result: List[ScExpression] = Nil): List[ScExpression] =
    contextWithStableType(originalPosition(element)) match {
      case Some(expr) => originalContextsWithStableType(expr.getContext, expr :: result)
      case None       => result
    }

  @tailrec
  private def contextWithStableType(element: PsiElement): Option[ScExpression] =
    element match {
      case null | _: ScalaFile => None
      case owner: ScExpression if owner.shouldntChangeModificationCount => Some(owner)
      case owner => contextWithStableType(owner.getContext)
    }

}