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
  private var myReturnTypeElement: SoftReference[Option[ScTypeElement]] = null
  private var myBodyExpression: SoftReference[Option[ScExpression]] = null
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
    this.annotations = annotations.map(StringRef.fromString(_))
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

  def getAnnotations: Array[String] = annotations.map(StringRef.toString(_))

  def getReturnTypeElement: Option[ScTypeElement] = {
    if (myReturnTypeElement != null && myReturnTypeElement.get != null) return myReturnTypeElement.get
    val res: Option[ScTypeElement] = {
      if (getReturnTypeText != "") {
        Some(ScalaPsiElementFactory.createTypeElementFromText(getReturnTypeText, getPsi, null))
      }
      else None
    }
    myReturnTypeElement = new SoftReference[Option[ScTypeElement]](res)
    res
  }

  def getBodyExpression: Option[ScExpression] = {
    if (myBodyExpression != null && myBodyExpression.get != null) return myBodyExpression.get
    val res: Option[ScExpression] = {
      if (getBodyText != "") {
        Some(ScalaPsiElementFactory.createExpressionWithContextFromText(getBodyText, getPsi, null))
      }
      else {
        None
      }
    }
    myBodyExpression = new SoftReference[Option[ScExpression]](res)
    res
  }

  def getBodyText: String = StringRef.toString(bodyText)

  def getReturnTypeText: String = StringRef.toString(typeText)

  def hasAssign: Boolean = assign

  def isImplicit: Boolean = _implicit
}