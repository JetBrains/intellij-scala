package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import api.statements.params.ScParameter
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.util.io.StringRef

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

class ScParameterStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
extends StubBaseWrapper[ScParameter](parent, elemType) with ScParameterStub {
  private var name: StringRef = _
  private var typeText: StringRef = _

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement],
          name: String, typeText: String) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = StringRef.fromString(name)
    this.typeText = StringRef.fromString(typeText)
  }

  def getName: String = StringRef.toString(name)

  def getTypeText: String = StringRef.toString(typeText)
}