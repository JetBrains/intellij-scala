package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScTemplateBodyImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateBodyStub
import org.jetbrains.plugins.scala.lang.psi.stubs.factories.ScStubSerializingElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTemplateBodyStubImpl

final class ScTemplateBodyElementType extends ScStubElementType[ScTemplateBody]("template body") {
  override def createElement(node: ASTNode): ScTemplateBody = new ScTemplateBodyImpl(node)
}

final class ScTemplateBodyStubFactory(elementType: ScTemplateBodyElementType)
  extends ScStubSerializingElementFactory[ScTemplateBodyStub, ScTemplateBody](elementType) {

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScTemplateBodyStub =
    new ScTemplateBodyStubImpl(parentStub, elementType)

  override def createStubImpl(templateBody: ScTemplateBody, parentStub: StubElement[_ <: PsiElement]): ScTemplateBodyStub =
    new ScTemplateBodyStubImpl(parentStub, elementType)

  override def createPsi(stub: ScTemplateBodyStub): ScTemplateBody = new ScTemplateBodyImpl(stub)
}
