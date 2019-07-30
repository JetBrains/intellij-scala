package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.util.{Key, ModificationTracker}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.caches.BlockModificationTracker._
import org.jetbrains.plugins.scala.caches.CachesUtil.scalaTopLevelModTracker
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.annotation.tailrec

class BlockModificationTracker private (element: PsiElement) extends ModificationTracker {

  private val topLevel = scalaTopLevelModTracker(element.getProject)

  def getModificationCount: Long = {
    topLevel.getModificationCount + originalModCount(element, 0L)
  }
}

object BlockModificationTracker {

  def apply(element: PsiElement): ModificationTracker =
    if (!element.isValid) ModificationTracker.NEVER_CHANGED
    else new BlockModificationTracker(element)

  private val originalPositionKey: Key[ScExpression] = Key.create("original.position.completion.key")

  //in completion we need to compute modification count of elements in "completion file"
  //we need to use original file for that to have up-to-date completion results
  def setOriginalPosition(element: PsiElement, original: PsiElement): Unit = {
    if (original != null) {
      for {
        elementContext  <- contextWithStableType(element)
        originalContext <- contextWithStableType(original)
      } {

        //consistent block modification count in completion file
        elementContext.putUserData(originalPositionKey, originalContext)

        //resolve should go to original file outside of context with stable type
        elementContext match {
          case elementContext: ScalaPsiElement =>
            elementContext.context = originalContext.getContext
            elementContext.child = originalContext
          case _ =>
        }

        assert(modificationCount(element) == modificationCount(original))
      }
    }
  }

  private def modificationCount(element: PsiElement) = BlockModificationTracker(element).getModificationCount

  private def originalPosition(element: ScExpression): ScExpression =
    Option(element)
      .flatMap(e => Option(e.getUserData(originalPositionKey)))
      .getOrElse(element)

  //goes from completion file to original file,
  //walks up the tree and sums modification counts of expressions with stable types
  @tailrec
  private def originalModCount(element: PsiElement, acc: Long): Long =
    contextWithStableType(element).map(originalPosition) match {
      case Some(expr) => originalModCount(expr.getContext, acc + expr.modificationCount)
      case None       => acc
    }

  @tailrec
  private def contextWithStableType(element: PsiElement): Option[ScExpression] =
    element match {
      case null | _: ScalaFile => None
      case owner: ScExpression if owner.shouldntChangeModificationCount => Some(owner)
      case owner => contextWithStableType(owner.getContext)
    }

}