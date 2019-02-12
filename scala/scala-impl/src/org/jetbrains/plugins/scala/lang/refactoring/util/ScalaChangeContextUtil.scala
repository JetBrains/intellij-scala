package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.psi._
import org.jetbrains.plugins.scala.conversion.copy.ScalaCopyPastePostProcessor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

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

  def collectDataForElement(element: PsiElement): Associations = element.getContainingFile match {
    case scalaFile: ScalaFile if !DumbService.getInstance(scalaFile.getProject).isDumb =>
      element.getTextRange match {
        case range if range.getStartOffset == 0 => null
        case range => ScalaCopyPastePostProcessor.collectAssociations(range)(scalaFile)
      }
    case _ => null
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
