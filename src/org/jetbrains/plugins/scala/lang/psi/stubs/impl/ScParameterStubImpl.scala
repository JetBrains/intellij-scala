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
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

class ScParameterStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
extends StubBaseWrapper[ScParameter](parent, elemType) with ScParameterStub {
  private var name: String = _
  private var typeText: String = _
  private var myTypeElement: SofterReference[Option[ScTypeElement]] = null
  private var stable: Boolean = false
  private var default: Boolean = false
  private var repeated: Boolean = false
  private var _isVal: Boolean = false
  private var _isVar: Boolean = false
  private var _isCallByName: Boolean = false
  private var myDefaultExpression: SofterReference[Option[ScExpression]] = null
  private var defaultExprText: Option[String] = None
  private var _deprecatedName: Option[String] = None

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: String, typeText: String, stable: Boolean, default: Boolean, repeated: Boolean,
          isVal: Boolean, isVar: Boolean, isCallByName: Boolean, defaultExprText: Option[String],
          deprecatedName: Option[String]) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = name
    this.typeText = typeText
    this.stable = stable
    this.default = default
    this.repeated = repeated
    this._isVar = isVar
    this._isVal = isVal
    this._isCallByName = isCallByName
    this.defaultExprText = defaultExprText
    this._deprecatedName = deprecatedName
  }

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: StringRef, typeText: StringRef, stable: Boolean, default: Boolean, repeated: Boolean,
          isVal: Boolean, isVar: Boolean, isCallByName: Boolean, defaultExprText: Option[String],
          deprecatedName: Option[String]) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = StringRef.toString(name)
    this.typeText = StringRef.toString(typeText)
    this.stable = stable
    this.default = default
    this.repeated = repeated
    this._isVar = isVar
    this._isVal = isVal
    this._isCallByName = isCallByName
    this.defaultExprText = defaultExprText
    this._deprecatedName = deprecatedName
  }

  def getName: String = name

  def getTypeText: String = typeText

  def getTypeElement: Option[ScTypeElement] = {
    if (myTypeElement != null) {
      val typeElement = myTypeElement.get
      if (typeElement != null && (typeElement.isEmpty || (typeElement.get.getContext eq getPsi))) return typeElement
    }
    val res: Option[ScTypeElement] =
      if (getTypeText != "") Some(ScalaPsiElementFactory.createTypeElementFromText(getTypeText, getPsi, null))
      else None
    myTypeElement = new SofterReference[Option[ScTypeElement]](res)
    res
  }

  def isStable: Boolean = stable

  def isDefaultParam: Boolean = default

  def isRepeated: Boolean = repeated

  def isVar: Boolean = _isVar

  def isVal: Boolean = _isVal

  def isCallByNameParameter: Boolean = _isCallByName

  def getDefaultExprText: Option[String] = defaultExprText

  def getDefaultExpr: Option[ScExpression] = {
    if (myDefaultExpression != null) {
      val expression = myDefaultExpression.get
      if (expression != null && (expression.isEmpty || (expression.get.getContext eq getPsi))) return expression
    }
    val res: Option[ScExpression] =
      getDefaultExprText match {
        case None => None
        case Some("") => None
        case Some(text) =>
          Some(ScalaPsiElementFactory.createExpressionWithContextFromText(text, getPsi, null))
      }
    myDefaultExpression = new SofterReference[Option[ScExpression]](res)
    res
  }

  def deprecatedName: Option[String] = _deprecatedName
}