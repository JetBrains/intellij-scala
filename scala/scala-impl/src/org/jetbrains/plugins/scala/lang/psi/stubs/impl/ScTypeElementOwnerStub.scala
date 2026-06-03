package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.util.SofterReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeElementFromText

trait ScTypeElementOwnerStub[E <: PsiElement] extends PsiOwner[E] {

  def typeText: Option[String]

  private[impl] var typeElementReference: SofterReference[Option[ScTypeElement]] = _

  def typeElement: Option[ScTypeElement] = {
    getFromOptionalReference(typeElementReference) {
      case (context, child) =>
        typeText.map {
          createTypeElementFromText(_, context, child)
        }
    } (typeElementReference = _)
  }
}

class ScTypeElementOwnerStubImpl[E <: PsiElement] private[impl](override val typeText: Option[String],
                                                                stubElement: StubElement[E])
  extends ScTypeElementOwnerStub[E] {
  override def getPsi: E = stubElement.getPsi
}