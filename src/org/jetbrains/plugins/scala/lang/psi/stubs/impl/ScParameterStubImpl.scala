package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import api.statements.params.ScParameter
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.util.io.StringRef
import api.base.types.ScTypeElement
import com.intellij.util.PatchedSoftReference
import psi.impl.ScalaPsiElementFactory

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

class ScParameterStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
extends StubBaseWrapper[ScParameter](parent, elemType) with ScParameterStub {
  private var name: StringRef = _
  private var typeText: StringRef = _
  private var myTypeElement: PatchedSoftReference[Option[ScTypeElement]] = null
  private var stable: Boolean = false
  private var default: Boolean = false
  private var repeated: Boolean = false
  private var _isVal: Boolean = false
  private var _isVar: Boolean = false

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: String, typeText: String, stable: Boolean, default: Boolean, repeated: Boolean,
          isVal: Boolean, isVar: Boolean) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = StringRef.fromString(name)
    this.typeText = StringRef.fromString(typeText)
    this.stable = stable
    this.default = default
    this.repeated = repeated
    this._isVar = isVar
    this._isVal = isVal
  }

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: StringRef, typeText: StringRef, stable: Boolean, default: Boolean, repeated: Boolean,
          isVal: Boolean, isVar: Boolean) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = name
    this.typeText = typeText
    this.stable = stable
    this.default = default
    this.repeated = repeated
    this._isVar = isVar
    this._isVal = isVal
  }

  def getName: String = StringRef.toString(name)

  def getTypeText: String = StringRef.toString(typeText)

  def getTypeElement: Option[ScTypeElement] = {
    if (myTypeElement != null && myTypeElement.get != null) return myTypeElement.get
    val res: Option[ScTypeElement] = {
      if (getTypeText != "") {
        Some(ScalaPsiElementFactory.createTypeElementFromText(getTypeText, getPsi, getPsi /*doesn't matter*/))
      }
      else None
    }
    myTypeElement = new PatchedSoftReference[Option[ScTypeElement]](res)
    res
  }

  def isStable: Boolean = stable

  def isDefaultParam: Boolean = default

  def isRepeated: Boolean = repeated

  def isVar: Boolean = _isVar

  def isVal: Boolean = _isVal
}