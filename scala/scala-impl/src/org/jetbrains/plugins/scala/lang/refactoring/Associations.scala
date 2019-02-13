package org.jetbrains.plugins.scala
package lang
package refactoring

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.conversion.copy.ScalaCopyPastePostProcessor

/**
  * Pavel Fatin
  */
case class Associations(override val associations: Array[Association])
  extends AssociationsData(associations, Associations)
    with Cloneable {

  override def clone(): Associations = copy()
}

object Associations extends AssociationsData.Companion(classOf[Associations], "ScalaReferenceData") {

  object Data {

    private val key = Key.create[Associations]("ASSOCIATIONS")

    def apply(element: PsiElement): Associations = element.getCopyableUserData(key)

    def update(element: PsiElement, associations: Associations): Unit = {
      element.putCopyableUserData(key, associations)
    }
  }

  def shiftFor(element: PsiElement, offsetChange: Int): Unit = Data(element) match {
    case null =>
    case Associations(associations) =>
      associations.foreach { association =>
        association.range = association.range.shiftRight(offsetChange)
      }
  }

  def restoreFor(movedElement: PsiElement): Unit = Data(movedElement) match {
    case null =>
    case Associations(associations) =>
      try {
        ScalaCopyPastePostProcessor.doRestoreAssociations(associations, movedElement.getTextRange.getStartOffset)(identity)(movedElement.getProject, movedElement.getContainingFile)
      } finally {
        Data(movedElement) = null
      }
  }

}