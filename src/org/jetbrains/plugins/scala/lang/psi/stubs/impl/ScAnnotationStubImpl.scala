package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.SofterReference
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeElementFromText

/**
  * User: Alexander Podkhalyuzin
  * Date: 22.06.2009
  */
class ScAnnotationStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                    elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                                    private val nameRef: StringRef,
                                                    private val typeTextRef: StringRef)
  extends StubBaseWrapper[ScAnnotation](parent, elementType) with ScAnnotationStub {

  private var typeElementReference: SofterReference[ScTypeElement] = null

  def name: String = StringRef.toString(nameRef)

  def typeText: String = StringRef.toString(typeTextRef)

  def typeElement: ScTypeElement = {
    if (typeElementReference != null) {
      typeElementReference.get match {
        case null =>
        case typeElement if typeElement.getContext eq getPsi =>
          return typeElement
      }
    }
    val result = createTypeElementFromText(typeText, getPsi, null)
    typeElementReference = new SofterReference[ScTypeElement](result)
    result
  }
}
