package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import com.intellij.openapi.util.Key
import com.intellij.psi._
import org.jetbrains.plugins.scala.conversion.copy.{Associations, ScalaCopyPastePostProcessor}

/**
  * Nikolay.Tropin
  * 2014-05-27
  */
object ScalaChangeContextUtil {

  object AssociationsData {

    private val key = Key.create[Associations]("ASSOCIATIONS")

    def apply(element: PsiElement): Associations = element.getCopyableUserData(key)

    def update(element: PsiElement, associations: Associations): Unit = {
      element.putCopyableUserData(key, associations)
    }
  }

  object MovedElementData {

    private val key = Key.create[PsiElement]("moved.element")

    def apply(element: PsiElement): PsiElement = element.getUserData(key)

    def update(element: PsiElement, movedElement: PsiElement): Unit = {
      element.putUserData(key, movedElement)
    }
  }

  private val processor = new ScalaCopyPastePostProcessor

  def encodeContextInfo(element: PsiElement): Unit = {
    AssociationsData(element) = collectDataForElement(element)
  }

  def movedMember(target: PsiElement): PsiElement = {
    val moved = MovedElementData(target)
    MovedElementData(target) = null
    moved
  }

  def shiftAssociations(element: PsiElement, offsetChange: Int): Unit = AssociationsData(element) match {
    case null =>
    case Associations(associations) =>
      associations.foreach { association =>
        association.range = association.range.shiftRight(offsetChange)
      }
  }

  def collectDataForElement(element: PsiElement): Associations = {
    val range = element.getTextRange
    processor.collectTransferableData(
      Array(range.getStartOffset),
      Array(range.getEndOffset)
    )(element.getContainingFile, null).orNull
  }

  def restoreAssociations(movedElement: PsiElement): Unit =
    AssociationsData(movedElement) match {
      case null =>
      case Associations(associations) =>
        try {
          ScalaCopyPastePostProcessor.doRestoreAssociations(associations, movedElement.getTextRange.getStartOffset)(identity)(movedElement.getProject, movedElement.getContainingFile)
        } finally {
          AssociationsData(movedElement) = null
        }
    }
}
