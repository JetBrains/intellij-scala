package org.jetbrains.plugins.scala.lang.psi.stubs.impl

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
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
extends StubBaseWrapper[ScFunction](parent, elemType) with ScFunctionStub {
  private var name: StringRef = _
  private var declaration: Boolean = false
  private var annotations: Seq[String] = Seq.empty
  private var typeText: StringRef = _
  private var bodyText: StringRef = _
  private var myReturnType: PatchedSoftReference[ScType] = null

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement],
          name: String, isDeclaration: Boolean, annotations: Seq[String], typeText: String, bodyText: String) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = StringRef.fromString(name)
    this.declaration = isDeclaration
    this.annotations = annotations
    this.typeText = StringRef.fromString(typeText)
    this.bodyText = StringRef.fromString(bodyText)
  }

  def getName: String = StringRef.toString(name)

  def isDeclaration = declaration

  def getAnnotations: Seq[String] = annotations

  def getReturnType: ScType = {
    if (myReturnType == null) {
      val res: ScType = {
        if (getReturnTypeText != "") {
          ScalaPsiElementFactory.createTypeFromText(getReturnTypeText, getPsi)
        } else if (getBodyText != "") {
          ScalaPsiElementFactory.createExpressionTypeFromText(getBodyText, getPsi)
        } else {
          types.Unit
        }
      }
      if (res != null)
      myReturnType = new PatchedSoftReference[ScType](res)
      else
      myReturnType = new PatchedSoftReference[ScType](types.Nothing)
      res
    } else myReturnType.get
  }

  def getBodyText: String = StringRef.toString(bodyText)

  def getReturnTypeText: String = StringRef.toString(typeText)
}