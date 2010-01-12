package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.io.StringRef
import java.lang.String
import api.base.types.{ScTypeElement, ScSelfTypeElement}

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.06.2009
 */

class ScSelfTypeElementStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                         elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
        extends StubBaseWrapper[ScSelfTypeElement](parent, elemType) with ScSelfTypeElementStub {
  private var name: StringRef = _
  private var typeElementText: StringRef = _

  def this(parent: StubElement[ParentPsi],
           elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
           name: String, typeElementText: String) = {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = StringRef.fromString(name)
    this.typeElementText = StringRef.fromString(typeElementText)
  }

  def getName: String = StringRef.toString(name)

  def getTypeElementText: String = typeElementText.toString
}