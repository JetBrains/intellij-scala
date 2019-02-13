package org.jetbrains.plugins.scala
package lang
package refactoring

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.conversion.copy.ScalaCopyPastePostProcessor

final class Associations private(override val associations: Array[Association])
  extends AssociationsData(associations, Associations)
    with Cloneable {

  override def clone(): Associations = new Associations(associations)

  override def canEqual(other: Any): Boolean = other.isInstanceOf[Associations]

  override def toString = s"Associations($associations)"
}

object Associations extends AssociationsData.Companion(classOf[Associations], "ScalaReferenceData") {

  def apply(associations: Array[Association]) = new Associations(associations)

  def unapply(associations: Associations): Some[Array[Association]] =
    Some(associations.associations)

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