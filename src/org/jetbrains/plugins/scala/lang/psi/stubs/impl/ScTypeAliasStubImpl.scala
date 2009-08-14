package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import api.base.types.ScTypeElement
import api.statements.ScTypeAlias
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.util.io.StringRef
import com.intellij.util.PatchedSoftReference
import psi.impl.ScalaPsiElementFactory

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
  private var myTypeElement: PatchedSoftReference[ScTypeElement] = null

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: String, isDeclaration: Boolean, typeElementText: String) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = StringRef.fromString(name)
    this.declaration = isDeclaration
    this.typeElementText = StringRef.fromString(typeElementText)
  }

  def getName: String = StringRef.toString(name)

  def isDeclaration = declaration

  def getTypeElement: ScTypeElement = {
    if (myTypeElement != null && myTypeElement.get != null) return myTypeElement.get
    if (getTypeElementText == "") return null
    val res: ScTypeElement = {
      ScalaPsiElementFactory.createTypeElementFromText(getTypeElementText, getPsi)
    }
    myTypeElement = new PatchedSoftReference[ScTypeElement](res)
    return res
  }

  def getTypeElementText: String = typeElementText.toString
}