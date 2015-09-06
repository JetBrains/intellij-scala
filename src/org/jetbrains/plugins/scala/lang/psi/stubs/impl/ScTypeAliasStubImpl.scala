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
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 *  User: Alexander Podkhalyuzin
 *  Date: 18.10.2008
 */

class ScTypeAliasStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
extends StubBaseWrapper[ScTypeAlias](parent, elemType) with ScTypeAliasStub {
  private var name: StringRef = _
  private var declaration: Boolean = false
  private var typeElementText: StringRef = _
  private var myTypeElement: SofterReference[ScTypeElement] = null
  private var lowerTypeElementText: StringRef = _
  private var myLowerTypeElement: SofterReference[ScTypeElement] = null
  private var upperTypeElementText: StringRef = _
  private var myUpperTypeElement: SofterReference[ScTypeElement] = null
  private var local: Boolean = false
  private var _stableQualifier: Boolean = false

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: String, isDeclaration: Boolean, typeElementText: String, lowerTypeElementText: String,
                  upperTypeElementText: String, isLocal: Boolean, stableQualifier: Boolean) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = StringRef.fromString(name)
    this.declaration = isDeclaration
    this.typeElementText = StringRef.fromString(typeElementText)
    this.lowerTypeElementText = StringRef.fromString(lowerTypeElementText)
    this.upperTypeElementText = StringRef.fromString(upperTypeElementText)
    this._stableQualifier = stableQualifier
    local = isLocal
  }

  def isLocal: Boolean = local

  def getName: String = StringRef.toString(name)

  def isDeclaration = declaration

  def getTypeElement: ScTypeElement = {
    if (myTypeElement != null) {
      val typeElement = myTypeElement.get
      if (typeElement != null && (typeElement.getContext eq getPsi)) return typeElement
    }
    if (getTypeElementText == "") return null
    val res: ScTypeElement = ScalaPsiElementFactory.createTypeElementFromText(getTypeElementText, getPsi, null)
    myTypeElement = new SofterReference[ScTypeElement](res)
    res
  }

  def getTypeElementText: String = typeElementText.toString

  def getUpperBoundTypeElement: ScTypeElement = {
    if (myUpperTypeElement != null) {
      val upperTypeElement = myUpperTypeElement.get
      if (upperTypeElement != null && (upperTypeElement.getContext eq getPsi)) return upperTypeElement
    }
    if (getUpperBoundElementText == "") return null
    val res: ScTypeElement = ScalaPsiElementFactory.createTypeElementFromText(getUpperBoundElementText, getPsi, null)
    myUpperTypeElement = new SofterReference[ScTypeElement](res)
    res
  }

  def getUpperBoundElementText: String = upperTypeElementText.toString

  def getLowerBoundTypeElement: ScTypeElement = {
    if (myLowerTypeElement != null) {
      val lowerTypeElement = myLowerTypeElement.get
      if (lowerTypeElement != null && (lowerTypeElement.getContext eq getPsi)) return lowerTypeElement
    }
    if (getLowerBoundElementText == "") return null
    val res: ScTypeElement = ScalaPsiElementFactory.createTypeElementFromText(getLowerBoundElementText, getPsi, null)
    myLowerTypeElement = new SofterReference[ScTypeElement](res)
    res
  }

  def getLowerBoundElementText: String = lowerTypeElementText.toString

  def isStableQualifier: Boolean = _stableQualifier
}