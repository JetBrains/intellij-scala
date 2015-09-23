package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.SofterReference
import com.intellij.util.SofterReference
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 *  User: Alexander Podkhalyuzin
 *  Date: 14.10.2008
 */

class ScFunctionStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
extends StubBaseWrapper[ScFunction](parent, elemType) with ScFunctionStub {
  private var name: StringRef = _
  private var declaration: Boolean = false
  private var annotations: Array[StringRef] = Array[StringRef]()
  private var typeText: StringRef = _
  private var bodyText: StringRef = _
  private var myReturnTypeElement: SofterReference[Option[ScTypeElement]] = null
  private var myBodyExpression: SofterReference[Option[ScExpression]] = null
  private var assign: Boolean = false
  private var _implicit: Boolean = false
  private var local: Boolean = false

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: String, isDeclaration: Boolean, annotations: Array[String], typeText: String, bodyText: String,
          assign: Boolean, isImplicit: Boolean, isLocal: Boolean) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = StringRef.fromString(name)
    this.declaration = isDeclaration
    this.annotations = annotations.map(StringRef.fromString)
    this.typeText = StringRef.fromString(typeText)
    this.bodyText = StringRef.fromString(bodyText)
    this.assign = assign
    _implicit = isImplicit
    local = isLocal
  }

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: StringRef, isDeclaration: Boolean, annotations: Array[StringRef], typeText: StringRef, bodyText: StringRef,
          assign: Boolean, isImplicit: Boolean, isLocal: Boolean) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = name
    this.declaration = isDeclaration
    this.annotations = annotations
    this.typeText = typeText
    this.bodyText = bodyText
    this.assign = assign
    _implicit = isImplicit
    local = isLocal
  }

  def isLocal: Boolean = local

  def getName: String = StringRef.toString(name)

  def isDeclaration = declaration

  def getAnnotations: Array[String] = annotations.map(StringRef.toString)

  def getReturnTypeElement: Option[ScTypeElement] = {
    if (myReturnTypeElement != null) {
      val returnTypeElement = myReturnTypeElement.get
      if (returnTypeElement != null && (returnTypeElement.isEmpty || (returnTypeElement.get.getContext eq getPsi))) {
        return returnTypeElement
      }
    }
    val res: Option[ScTypeElement] =
      if (getReturnTypeText != "") {
        Some(ScalaPsiElementFactory.createTypeElementFromText(getReturnTypeText, getPsi, null))
      } else None
    myReturnTypeElement = new SofterReference[Option[ScTypeElement]](res)
    res
  }

  def getBodyExpression: Option[ScExpression] = {
    if (myBodyExpression != null) {
      val body = myBodyExpression.get
      if (body != null && (body.isEmpty || (body.get.getContext eq getPsi))) return body
    }
    val res: Option[ScExpression] =
      if (getBodyText != "") {
        Some(ScalaPsiElementFactory.createExpressionWithContextFromText(getBodyText, getPsi, null))
      } else None
    myBodyExpression = new SofterReference[Option[ScExpression]](res)
    res
  }

  def getBodyText: String = StringRef.toString(bodyText)

  def getReturnTypeText: String = StringRef.toString(typeText)

  def hasAssign: Boolean = assign

  def isImplicit: Boolean = _implicit
}