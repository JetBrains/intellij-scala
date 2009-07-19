package org.jetbrains.plugins.scala.lang.psi.stubs.impl


import api.base.ScFieldId
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.io.StringRef

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.07.2009
 */

class ScFieldIdStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
extends StubBaseWrapper[ScFieldId](parent, elemType) with ScFieldIdStub {
  private var name: StringRef = _

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement],
          name: String) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = StringRef.fromString(name)
  }

  def getName: String = StringRef.toString(name)
}