package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.SofterReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.06.2009
  */
class ScTypeParamStubImpl(parent: StubElement[_ <: PsiElement],
                          elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                          name: String,
                          val text: String,
                          val lowerBoundText: Option[String],
                          val upperBoundText: Option[String],
                          val viewBoundsTexts: Array[String],
                          val contextBoundsTexts: Array[String],
                          val isCovariant: Boolean,
                          val isContravariant: Boolean,
                          val containingFileName: String)
  extends ScNamedStubBase[ScTypeParam](parent, elementType, name)
    with ScTypeParamStub with ScBoundsOwnerStub[ScTypeParam] {

  private var viewElementsReferences: SofterReference[Seq[ScTypeElement]] = null
  private var contextElementsReferences: SofterReference[Seq[ScTypeElement]] = null

  def viewBoundsTypeElements: Seq[ScTypeElement] = {
    getFromReference(viewElementsReferences) {
      case (context, child) =>
        viewBoundsTexts.map {
          createTypeElementFromText(_, context, child)
        }
    } (viewElementsReferences = _)
  }

  def contextBoundsTypeElements: Seq[ScTypeElement] = {
    getFromReference(contextElementsReferences) {
      case (context, child) =>
        contextBoundsTexts.map {
          createTypeElementFromText(_, context, child)
        }
    } (contextElementsReferences = _)
  }
}