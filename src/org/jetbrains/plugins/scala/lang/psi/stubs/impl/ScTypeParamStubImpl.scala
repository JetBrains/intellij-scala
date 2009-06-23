package org.jetbrains.plugins.scala.lang.psi.stubs.impl


import api.base.ScAccessModifier
import api.base.types.ScTypeElement
import api.statements.params.ScTypeParam
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.io.StringRef
import com.intellij.util.PatchedSoftReference
import java.lang.String
import psi.impl.ScalaPsiElementFactory
/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScTypeParamStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
        extends StubBaseWrapper[ScTypeParam](parent, elemType) with ScTypeParamStub {
  private var name: StringRef = _
  private var upperText: StringRef = _
  private var lowerText: StringRef = _
  private var viewText: StringRef = _
  private var upperElement: PatchedSoftReference[Option[ScTypeElement]] = null
  private var lowerElement: PatchedSoftReference[Option[ScTypeElement]] = null
  private var viewElement: PatchedSoftReference[Option[ScTypeElement]] = null

  def getName: String = StringRef.toString(name)

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement],
          name: String, upperText: String, lowerText: String, viewText: String) {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = StringRef.fromString(name)
    this.upperText = StringRef.fromString(upperText)
    this.lowerText = StringRef.fromString(lowerText)
    this.viewText = StringRef.fromString(viewText)
  }

  def getUpperText: String = upperText.toString

  def getLowerTypeElement: Option[ScTypeElement] = {
    if (lowerElement != null) return lowerElement.get
    val res: Option[ScTypeElement] = {
      if (getLowerText != "")
        Some(ScalaPsiElementFactory.createTypeElementFromText(getLowerText, getPsi))
      else None
    }
    lowerElement = new PatchedSoftReference[Option[ScTypeElement]](res)
    return res
  }

  def getUpperTypeElement: Option[ScTypeElement] = {
    if (upperElement != null) return upperElement.get
    val res: Option[ScTypeElement] = {
      if (getUpperText != "")
        Some(ScalaPsiElementFactory.createTypeElementFromText(getUpperText, getPsi))
      else None
    }
    upperElement = new PatchedSoftReference[Option[ScTypeElement]](res)
    return res
  }

  def getLowerText: String = lowerText.toString

  def getViewText: String = viewText.toString

  def getViewTypeElement: Option[ScTypeElement] = {
    if (viewElement != null) return viewElement.get
    val res: Option[ScTypeElement] = {
      if (getViewText != "")
        Some(ScalaPsiElementFactory.createTypeElementFromText(getViewText, getPsi))
      else None
    }
    viewElement = new PatchedSoftReference[Option[ScTypeElement]](res)
    return res
  }
}