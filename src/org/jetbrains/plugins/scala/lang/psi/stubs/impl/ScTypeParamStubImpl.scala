package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.reference.SoftReference
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScTypeParamStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
        extends StubBaseWrapper[ScTypeParam](parent, elemType) with ScTypeParamStub {
  private var name: StringRef = _
  private var upperText: StringRef = _
  private var lowerText: StringRef = _
  private var viewText: Seq[StringRef] = _
  private var contextBoundText: Seq[StringRef] = _
  private var upperElement: SoftReference[Option[ScTypeElement]] = new SoftReference(null)
  private var lowerElement: SoftReference[Option[ScTypeElement]] = new SoftReference(null)
  private var viewElement: SoftReference[Seq[ScTypeElement]] = new SoftReference(null)
  private var contextBoundElement: SoftReference[Seq[ScTypeElement]] = new SoftReference(null)
  private var covariant: Boolean = _
  private var contravariant: Boolean = _
  private var positionInFile: Int = _
  private var containingFileName: String = ""
  private var _typeParameterText: String = ""

  def getName: String = StringRef.toString(name)

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: StringRef, upperText: StringRef, lowerText: StringRef, viewText: Seq[StringRef], contextBoundText: Seq[StringRef],
          covariant: Boolean, contravariant: Boolean, position: Int, fileName: StringRef, typeParameterText: StringRef) {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = name
    this.upperText = upperText
    this.lowerText = lowerText
    this.viewText = viewText
    this.contextBoundText = contextBoundText
    this.covariant = covariant
    this.contravariant = contravariant
    this.positionInFile = position
    this.containingFileName = StringRef.toString(fileName)
    this._typeParameterText = StringRef.toString(typeParameterText)
  }

  def getPositionInFile: Int = positionInFile

  def isCovariant: Boolean = covariant

  def isContravariant: Boolean = contravariant

  def getUpperText: String = upperText.toString

  def typeParameterText: String = _typeParameterText

  def getLowerTypeElement: Option[ScTypeElement] = {
    val lowerTypeElement = lowerElement.get
    if (lowerTypeElement != null && (lowerTypeElement.isEmpty || (lowerTypeElement.get.getContext eq getPsi))) {
      return lowerTypeElement
    }
    val res: Option[ScTypeElement] =
      if (getLowerText != "") Some(ScalaPsiElementFactory.createTypeElementFromText(getLowerText, getPsi, null))
      else None
    lowerElement = new SoftReference[Option[ScTypeElement]](res)
    res
  }

  def getUpperTypeElement: Option[ScTypeElement] = {
    val upperTypeElement = upperElement.get
    if (upperTypeElement != null && (upperTypeElement.isEmpty || (upperTypeElement.get.getContext eq getPsi))) {
      return upperTypeElement
    }
    val res: Option[ScTypeElement] =
      if (getUpperText != "") Some(ScalaPsiElementFactory.createTypeElementFromText(getUpperText, getPsi, null))
      else None
    upperElement = new SoftReference[Option[ScTypeElement]](res)
    res
  }

  def getLowerText: String = lowerText.toString

  def getViewText: Seq[String] = viewText.map(_.toString)

  def getContextBoundText: Seq[String] = contextBoundText.map(_.toString)

  def getViewTypeElement: Seq[ScTypeElement] = {
    val viewTypeElements = viewElement.get
    if (viewTypeElements != null && viewTypeElements.forall(_.getContext.eq(getPsi))) return viewTypeElements
    val res: Seq[ScTypeElement] = getViewText.map(ScalaPsiElementFactory.createTypeElementFromText(_, getPsi, null))
    viewElement = new SoftReference(res)
    res
  }

  def getContextBoundTypeElement: Seq[ScTypeElement] = {
    val contextTypeElements = contextBoundElement.get
    if (contextTypeElements != null && contextTypeElements.forall(_.getContext.eq(getPsi))) return contextTypeElements
    val res: Seq[ScTypeElement] = getContextBoundText.map(ScalaPsiElementFactory.createTypeElementFromText(_, getPsi, null))
    contextBoundElement = new SoftReference(res)
    res
  }

  def getContainingFileName: String = containingFileName
}