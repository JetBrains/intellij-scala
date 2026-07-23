package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScTemplateParentsImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateParentsStub
import org.jetbrains.plugins.scala.lang.psi.stubs.factories.ScStubSerializingElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTemplateParentsStubImpl

final class ScTemplateParentsElementType extends ScStubElementType[ScTemplateParents]("template parents") {
  override def createElement(node: ASTNode): ScTemplateParents = new ScTemplateParentsImpl(node)
}

final class ScTemplateParentsStubFactory(elementType: ScTemplateParentsElementType)
  extends ScStubSerializingElementFactory[ScTemplateParentsStub, ScTemplateParents](elementType) {

  override def serialize(stub: ScTemplateParentsStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeNames(stub.parentClausesText)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScTemplateParentsStub =
    new ScTemplateParentsStubImpl(
      parentStub,
      elementType,
      parentClausesText = dataStream.readNames
    )

  override def createStubImpl(templateParents: ScTemplateParents, parentStub: StubElement[_ <: PsiElement]): ScTemplateParentsStub =
    new ScTemplateParentsStubImpl(
      parentStub,
      elementType,
      parentClausesText = templateParents.parentClauses.toArray.map(_.getText)
    )

  override def createPsi(stub: ScTemplateParentsStub): ScTemplateParents = new ScTemplateParentsImpl(stub)
}
