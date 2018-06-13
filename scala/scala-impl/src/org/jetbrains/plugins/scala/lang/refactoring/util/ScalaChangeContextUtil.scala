package org.jetbrains.plugins.scala
package lang.refactoring.util

import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi._
import org.jetbrains.plugins.scala.conversion.copy.{Associations, ScalaCopyPastePostProcessor}

/**
 * Nikolay.Tropin
 * 2014-05-27
 */
object ScalaChangeContextUtil {

  private val ASSOCIATIONS_KEY: Key[Associations] = Key.create("ASSOCIATIONS")
  private val MOVED_ELEMENT_KEY: Key[PsiElement] = Key.create("moved.element")

  val processor = new ScalaCopyPastePostProcessor

  def encodeContextInfo(scope: Seq[PsiElement]) {
    scope.foreach { elem =>
      val associations = collectDataForElement(elem)
      elem.putCopyableUserData(ASSOCIATIONS_KEY, associations)
    }
  }

  def storeMovedMember(moved: PsiElement, target: PsiElement): Unit = {
    target.putUserData(MOVED_ELEMENT_KEY, moved)
  }

  def getMovedMember(target: PsiElement): PsiElement = {
    val moved = target.getUserData(MOVED_ELEMENT_KEY)
    target.putUserData(MOVED_ELEMENT_KEY, null)
    moved
  }

  def decodeContextInfo(scope: Seq[PsiElement]): Unit = {
    scope.foreach(restoreForElement)
  }

  def shiftAssociations(elem: PsiElement, offsetChange: Int) {
    elem.getCopyableUserData(ASSOCIATIONS_KEY) match {
      case null =>
      case as: Associations =>  as.associations.foreach(a => a.range = a.range.shiftRight(offsetChange))
    }
  }

  private def collectDataForElement(elem: PsiElement) = {
    val range: TextRange = elem.getTextRange
    val associations = processor.collectTransferableData(elem.getContainingFile, null,
      Array[Int](range.getStartOffset), Array[Int](range.getEndOffset))

    if (associations.isEmpty) null else associations.get(0)
  }

  private def restoreForElement(elem: PsiElement) {
    val movedElement = Option(getMovedMember(elem)).getOrElse(elem)
    val associations: Associations = movedElement.getCopyableUserData(ASSOCIATIONS_KEY)

    movedElement.putCopyableUserData(ASSOCIATIONS_KEY, null)

    if (associations != null) {
      processor.restoreAssociations(associations, movedElement.getContainingFile, movedElement.getTextRange.getStartOffset, elem.getProject)
    }
  }
}
