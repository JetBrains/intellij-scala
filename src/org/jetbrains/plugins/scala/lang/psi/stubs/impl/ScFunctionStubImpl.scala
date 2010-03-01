package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import api.base.types.ScTypeElement
import api.expr.ScExpression
import api.statements.ScFunction
import api.toplevel.typedef.ScTemplateDefinition
import com.intellij.psi.impl.cache.TypeInfo
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.psi.{PsiElement, PsiType}
import com.intellij.util.io.StringRef
import com.intellij.util.PatchedSoftReference
import psi.impl.ScalaPsiElementFactory
import types.ScType
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
  private var myReturnTypeElement: PatchedSoftReference[Option[ScTypeElement]] = null
  private var myBodyExpression: PatchedSoftReference[Option[ScExpression]] = null
  private var assign: Boolean = false

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: String, isDeclaration: Boolean, annotations: Array[String], typeText: String, bodyText: String,
          assign: Boolean) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = StringRef.fromString(name)
    this.declaration = isDeclaration
    this.annotations = annotations.map(StringRef.fromString(_))
    this.typeText = StringRef.fromString(typeText)
    this.bodyText = StringRef.fromString(bodyText)
    this.assign = assign
  }

  def getName: String = StringRef.toString(name)

  def isDeclaration = declaration

  def getAnnotations: Array[String] = annotations.map(StringRef.toString(_))

  def getReturnTypeElement: Option[ScTypeElement] = {
    if (myReturnTypeElement != null && myReturnTypeElement.get != null) return myReturnTypeElement.get
    val res: Option[ScTypeElement] = {
      if (getReturnTypeText != "") {
        Some(ScalaPsiElementFactory.createTypeElementFromText(getReturnTypeText, getPsi))
      }
      else None
    }
    myReturnTypeElement = new PatchedSoftReference[Option[ScTypeElement]](res)
    res
  }

  def getBodyExpression: Option[ScExpression] = {
    if (myBodyExpression != null && myBodyExpression.get != null) return myBodyExpression.get
    val res: Option[ScExpression] = {
      if (getBodyText != "") {
        Some(ScalaPsiElementFactory.createExpressionWithContextFromText(getBodyText, getPsi))
      }
      else {
        None
      }
    }
    myBodyExpression = new PatchedSoftReference[Option[ScExpression]](res)
    res
  }

  def getBodyText: String = StringRef.toString(bodyText)

  def getReturnTypeText: String = StringRef.toString(typeText)

  def hasAssign: Boolean = assign
}