package org.jetbrains.plugins.scala.lang.psi.stubs.impl


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.SofterReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScContextBound, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeParamStub

class ScTypeParamStubImpl(parent: StubElement[_ <: PsiElement],
                          elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                          name: String,
                          override val text: String,
                          override val lowerBoundText: Option[String],
                          override val upperBoundText: Option[String],
                          override val viewBoundsTexts: Array[String],
                          override val contextBoundsTexts: Array[String],
                          override val isCovariant: Boolean,
                          override val isContravariant: Boolean,
                          override val containingFileName: String)
  extends ScNamedStubBase[ScTypeParam](parent, elementType, name)
    with ScTypeParamStub with ScBoundsOwnerStub[ScTypeParam] {

  private var viewElementsReferences: SofterReference[Seq[ScTypeElement]] = _
  private var contextElementsReferences: SofterReference[Seq[ScContextBound]] = _

  override def viewBoundsTypeElements: Seq[ScTypeElement] = {
    getFromReference(viewElementsReferences) {
      case (context, child) =>
        viewBoundsTexts.map {
          createTypeElementFromText(_, context, child)
        }.toSeq
    } (viewElementsReferences = _)
  }

  override def contextBounds: Seq[ScContextBound] = {
    getFromReference(contextElementsReferences) {
      case (context, child) =>
        contextBoundsTexts.map {
          createContextBoundFromText(_, context, child)
        }.toSeq
    } (contextElementsReferences = _)
  }
}