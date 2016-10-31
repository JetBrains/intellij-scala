package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.util.SofterReference
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeElementFromText
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.MaybeStringRefExt

/**
  * @author adkozlov
  */
trait ScTypeElementOwnerStub[E <: PsiElement] extends PsiOwner[E] {
  protected[impl] val typeTextRef: Option[StringRef]

  def typeText: Option[String] = typeTextRef.asString

  private[impl] var typeElementReference: SofterReference[Option[ScTypeElement]] = null

  def typeElement: Option[ScTypeElement] = {
    typeElementReference = updateOptionalReference(typeElementReference) {
      case (context, child) =>
        typeText.map {
          createTypeElementFromText(_, context, child)
        }
    }
    typeElementReference.get
  }
}

class ScTypeElementOwnerStubImpl[E <: PsiElement] private[impl](override protected[impl] val typeTextRef: Option[StringRef],
                                                                stubElement: StubElement[E])
  extends ScTypeElementOwnerStub[E] {
  override def getPsi: E = stubElement.getPsi
}