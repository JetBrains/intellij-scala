package org.jetbrains.plugins.scala.lang.psi.stubs.impl
import api.toplevel.templates.ScExtendsBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}

/**
 * @author ilyas
 */

class ScExtendsBlockStubImpl(parent: StubElement[_ <: PsiElement], elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
extends StubBaseWrapper[ScExtendsBlock](parent, elemType) with ScExtendsBlockStub {
  //todo implement me!

}