package org.jetbrains.plugins.scala
package lang.refactoring.util

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.openapi.util.{TextRange, Key}
import org.jetbrains.plugins.scala.conversion.copy.{Associations, ScalaCopyPastePostProcessor}

/**
 * Nikolay.Tropin
 * 2014-05-27
 */
object ScalaChangeContextUtil {

  private val ASSOCIATIONS_KEY: Key[Associations] = Key.create("ASSOCIATIONS")
  val processor = new ScalaCopyPastePostProcessor

  def encodeContextInfo(scope: Seq[PsiElement]) {
    def collectDataForElement(elem: PsiElement) = {
      val range: TextRange = elem.getTextRange
      val associations = processor.collectTransferableData(elem.getContainingFile, null,
        Array[Int](range.getStartOffset), Array[Int](range.getEndOffset))
      elem.putCopyableUserData(ASSOCIATIONS_KEY, associations)
    }
    scope.foreach(collectDataForElement)
  }

  def decodeContextInfo(scope: Seq[PsiElement]) {
    def restoreForElement(elem: PsiElement) {
      val associations: Associations = elem.getCopyableUserData(ASSOCIATIONS_KEY)
      if (associations != null) {
        try {
          processor.restoreAssociations(associations, elem.getContainingFile, elem.getTextRange.getStartOffset, elem.getProject)
        }
        finally {
          elem.putCopyableUserData(ASSOCIATIONS_KEY, null)
        }
      }
    }
    scope.foreach(restoreForElement)
  }

  def shiftAssociations(elem: PsiElement, offsetChange: Int) {
    elem.getCopyableUserData(ASSOCIATIONS_KEY) match {
      case null =>
      case as: Associations =>  as.associations.foreach(a => a.range = a.range.shiftRight(offsetChange))
    }
  }
}
