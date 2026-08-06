package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtensionBody
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScExtensionBodyImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExtensionBodyStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScExtensionBodyStubImpl

class ScExtensionBodyElementType
    extends ScStubElementType[ScExtensionBodyStub, ScExtensionBody]("extension body") {

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScExtensionBodyStub =
    new ScExtensionBodyStubImpl(parentStub, this)

  override def createStubImpl(
    extBody:    ScExtensionBody,
    parentStub: StubElement[_ <: PsiElement]
  ): ScExtensionBodyStub =
    new ScExtensionBodyStubImpl(parentStub, this)

  override def createElement(node: ASTNode): ScExtensionBody =
    new ScExtensionBodyImpl(node)

  override def createPsi(stub: ScExtensionBodyStub): ScExtensionBody =
    new ScExtensionBodyImpl(stub)
}
