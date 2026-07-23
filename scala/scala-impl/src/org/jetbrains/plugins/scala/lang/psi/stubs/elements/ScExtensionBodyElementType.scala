package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtensionBody
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScExtensionBodyImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExtensionBodyStub
import org.jetbrains.plugins.scala.lang.psi.stubs.factories.ScStubSerializingElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScExtensionBodyStubImpl

final class ScExtensionBodyElementType extends ScStubElementType[ScExtensionBody]("extension body") {
  override def createElement(node: ASTNode): ScExtensionBody = new ScExtensionBodyImpl(node)
}

final class ScExtensionBodyStubFactory(elementType: ScExtensionBodyElementType)
  extends ScStubSerializingElementFactory[ScExtensionBodyStub, ScExtensionBody](elementType) {

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScExtensionBodyStub =
    new ScExtensionBodyStubImpl(parentStub, elementType)

  override def createStubImpl(extBody: ScExtensionBody, parentStub: StubElement[_ <: PsiElement]): ScExtensionBodyStub =
    new ScExtensionBodyStubImpl(parentStub, elementType)

  override def createPsi(stub: ScExtensionBodyStub): ScExtensionBody = new ScExtensionBodyImpl(stub)
}
