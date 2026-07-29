package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScTemplateParentsImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateParentsStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTemplateParentsStubImpl

final class ScTemplateParentsElementType extends ScalaStubBasedElementType[ScTemplateParentsStub, ScTemplateParents](ScTemplateParentsElementType.DebugName) {
  override def createElement(node: ASTNode): ScTemplateParents = new ScTemplateParentsImpl(node)
}

object ScTemplateParentsElementType {
  val DebugName = "template parents"
}

class ScTemplateParentsStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScTemplateParentsStub, ScTemplateParents] {

  override def serialize(stub: ScTemplateParentsStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeNames(stub.parentClausesText)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScTemplateParentsStub =
    new ScTemplateParentsStubImpl(
      parentStub,
      elementType,
      parentClausesText = dataStream.readNames
    )

  override def createStub(templateParents: ScTemplateParents, parentStub: StubElement[_ <: PsiElement]): ScTemplateParentsStub =
    ScStubElementType.Processing.run {
      new ScTemplateParentsStubImpl(
        parentStub,
        elementType,
        parentClausesText = templateParents.parentClauses.toArray.map(_.getText))
    }

  override def createPsi(stub: ScTemplateParentsStub): ScTemplateParents = new ScTemplateParentsImpl(stub)

  override def indexStub(stub: ScTemplateParentsStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScTemplateParentsElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
