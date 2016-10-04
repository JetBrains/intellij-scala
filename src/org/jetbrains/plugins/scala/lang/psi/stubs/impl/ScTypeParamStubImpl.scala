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
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.{MaybeStringRefExt, StringRefArrayExt, StubBaseExt}

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.06.2009
  */
class ScTypeParamStubImpl(parent: StubElement[_ <: PsiElement],
                          elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                          private val nameRef: StringRef,
                          private val textRef: StringRef,
                          private val lowerBoundTextRef: Option[StringRef],
                          private val upperBoundTextRef: Option[StringRef],
                          private val viewBoundsTextRefs: Array[StringRef],
                          private val contextBoundsTextRefs: Array[StringRef],
                          val isCovariant: Boolean,
                          val isContravariant: Boolean,
                          private val containingFileNameRef: StringRef,
                          val positionInFile: Int)
  extends StubBase[ScTypeParam](parent, elementType) with ScTypeParamStub {
  private var upperElementReference: SofterReference[Option[ScTypeElement]] = null
  private var lowerElementReference: SofterReference[Option[ScTypeElement]] = null
  private var viewElementsReferences: SofterReference[Seq[ScTypeElement]] = null
  private var contextElementsReferences: SofterReference[Seq[ScTypeElement]] = null

  def getName: String = StringRef.toString(nameRef)

  override def text: String = StringRef.toString(textRef)

  override def containingFileName: String = StringRef.toString(containingFileNameRef)

  override def lowerBoundText: Option[String] = lowerBoundTextRef.asString

  def lowerBoundTypeElement: Option[ScTypeElement] = {
    lowerElementReference = this.updateOptionalReference(lowerElementReference) {
      case (context, child) =>
        lowerBoundText.map {
          createTypeElementFromText(_, context, child)
        }
    }
    lowerElementReference.get
  }

  override def upperBoundText: Option[String] = upperBoundTextRef.asString

  def upperBoundTypeElement: Option[ScTypeElement] = {
    upperElementReference = this.updateOptionalReference(upperElementReference) {
      case (context, child) =>
        upperBoundText.map {
          createTypeElementFromText(_, context, child)
        }
    }
    upperElementReference.get
  }

  override def viewBoundsTexts: Array[String] = viewBoundsTextRefs.asStrings

  def viewBoundsTypeElements: Seq[ScTypeElement] = {
    viewElementsReferences = this.updateReference(viewElementsReferences) {
      case (context, child) =>
        viewBoundsTexts.map {
          createTypeElementFromText(_, context, child)
        }
    }
    viewElementsReferences.get
  }

  override def contextBoundsTexts: Array[String] = contextBoundsTextRefs.asStrings

  def contextBoundsTypeElements: Seq[ScTypeElement] = {
    contextElementsReferences = this.updateReference(contextElementsReferences) {
      case (context, child) =>
        contextBoundsTexts.map {
          createTypeElementFromText(_, context, child)
        }
    }
    contextElementsReferences.get
  }
}