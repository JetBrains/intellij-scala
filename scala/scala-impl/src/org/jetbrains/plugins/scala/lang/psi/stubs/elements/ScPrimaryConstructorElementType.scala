package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScPrimaryConstructorImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPrimaryConstructorStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScPrimaryConstructorStubImpl

class ScPrimaryConstructorElementType extends ScStubElementType[ScPrimaryConstructorStub, ScPrimaryConstructor]("primary constructor") {
  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScPrimaryConstructorStub =
    new ScPrimaryConstructorStubImpl(parentStub, this)

  override def createStubImpl(constructor: ScPrimaryConstructor, parentStub: StubElement[_ <: PsiElement]): ScPrimaryConstructorStub =
    new ScPrimaryConstructorStubImpl(parentStub, this)

  override def createElement(node: ASTNode): ScPrimaryConstructor = new ScPrimaryConstructorImpl(node)

  override def createPsi(stub: ScPrimaryConstructorStub): ScPrimaryConstructor = new ScPrimaryConstructorImpl(stub)
}