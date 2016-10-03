package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.SofterReference
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeElementFromText
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.{MaybeStringRefExt, StubBaseExt}

/**
  * User: Alexander Podkhalyuzin
  * Date: 22.06.2009
  */
class ScAnnotationStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                    elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                                    private val nameRef: Option[StringRef],
                                                    private val typeTextRef: Option[StringRef])
  extends StubBase[ScAnnotation](parent, elementType) with ScAnnotationStub {

  private var typeElementReference: SofterReference[ScTypeElement] = null

  def name: Option[String] = nameRef.asString

  def typeText: Option[String] = typeTextRef.asString

  def typeElement: ScTypeElement = {
    typeElementReference = this.updateReference(typeElementReference) {
      createTypeElementFromText(typeText.getOrElse(""), _, _)
    }
    typeElementReference.get
  }
}
