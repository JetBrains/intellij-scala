package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


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
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
        extends StubBaseWrapper[ScTypeParam](parent, elemType) with ScTypeParamStub {
  private var name: StringRef = _
  private var upperText: StringRef = _
  private var lowerText: StringRef = _
  private var viewText: Array[StringRef] = _
  private var contextBoundText: Array[StringRef] = _
  private var upperElement: PatchedSoftReference[Option[ScTypeElement]] = null
  private var lowerElement: PatchedSoftReference[Option[ScTypeElement]] = null
  private var viewElement: Array[PatchedSoftReference[ScTypeElement]] = null
  private var contextBoundElement: Array[PatchedSoftReference[ScTypeElement]] = null
  private var covariant: Boolean = _
  private var contravariant: Boolean = _
  private var positionInFile: Int = _
  private var containingFileName: String = ""

  def getName: String = StringRef.toString(name)

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: String, upperText: String, lowerText: String, viewText: Array[String], contextBoundText: Array[String],
          covariant: Boolean, contravariant: Boolean, position: Int, fileName: String) {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = StringRef.fromString(name)
    this.upperText = StringRef.fromString(upperText)
    this.lowerText = StringRef.fromString(lowerText)
    this.viewText = viewText.map(StringRef.fromString(_))
    this.contextBoundText = contextBoundText.map(StringRef.fromString(_))
    this.covariant = covariant
    this.contravariant = contravariant
    this.positionInFile = position
    this.containingFileName = fileName
  }

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: StringRef, upperText: StringRef, lowerText: StringRef, viewText: Array[StringRef], contextBoundText: Array[StringRef],
          covariant: Boolean, contravariant: Boolean, position: Int, fileName: StringRef) {
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
  }

  def getPositionInFile: Int = positionInFile

  def isCovariant: Boolean = covariant

  def isContravariant: Boolean = contravariant

  def getUpperText: String = upperText.toString

  def getLowerTypeElement: Option[ScTypeElement] = {
    if (lowerElement != null && lowerElement.get != null) return lowerElement.get
    val res: Option[ScTypeElement] = {
      if (getLowerText != "")
        Some(ScalaPsiElementFactory.createTypeElementFromText(getLowerText, getPsi, null))
      else None
    }
    lowerElement = new PatchedSoftReference[Option[ScTypeElement]](res)
    res
  }

  def getUpperTypeElement: Option[ScTypeElement] = {
    if (upperElement != null && upperElement.get != null) return upperElement.get
    val res: Option[ScTypeElement] = {
      if (getUpperText != "")
        Some(ScalaPsiElementFactory.createTypeElementFromText(getUpperText, getPsi, null))
      else None
    }
    upperElement = new PatchedSoftReference[Option[ScTypeElement]](res)
    res
  }

  def getLowerText: String = lowerText.toString

  def getViewText: Array[String] = viewText.map(_.toString)

  def getContextBoundText: Array[String] = contextBoundText.map(_.toString)

  def getViewTypeElement: Array[ScTypeElement] = {
    if (viewElement != null && viewElement.forall(_.get ne null)) return viewElement.map(_.get)
    val res: Array[ScTypeElement] = getViewText.map(ScalaPsiElementFactory.createTypeElementFromText(_, getPsi, null))
    viewElement = res.map(new PatchedSoftReference[ScTypeElement](_))
    res
  }

  def getContextBoundTypeElement: Array[ScTypeElement] = {
    if (contextBoundElement != null && contextBoundElement.forall(_.get ne null)) return contextBoundElement.map(_.get)
    val res: Array[ScTypeElement] = getContextBoundText.map(ScalaPsiElementFactory.createTypeElementFromText(_, getPsi, null))
    contextBoundElement = res.map(new PatchedSoftReference[ScTypeElement](_))
    res
  }

  def getContainingFileName: String = containingFileName
}