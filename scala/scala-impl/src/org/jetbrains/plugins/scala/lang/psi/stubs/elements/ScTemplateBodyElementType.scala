package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScTemplateBodyImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateBodyStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTemplateBodyStubImpl

class ScTemplateBodyElementType extends ScalaStubBasedElementType[ScTemplateBodyStub, ScTemplateBody](ScTemplateBodyElementType.DebugName) {
  override def createElement(node: ASTNode): ScTemplateBody = new ScTemplateBodyImpl(node)
}

object ScTemplateBodyElementType {
  val DebugName = "template body"
}

class ScTemplateBodyStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScTemplateBodyStub, ScTemplateBody] {

  override def serialize(stub: ScTemplateBodyStub, dataStream: StubOutputStream): Unit = {}

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScTemplateBodyStub =
    new ScTemplateBodyStubImpl(parentStub, elementType)

  override def createStub(templateBody: ScTemplateBody, parentStub: StubElement[_ <: PsiElement]): ScTemplateBodyStub =
    ScStubElementType.Processing.run {
      new ScTemplateBodyStubImpl(parentStub, elementType)
    }

  override def createPsi(stub: ScTemplateBodyStub): ScTemplateBody = new ScTemplateBodyImpl(stub)

  override def indexStub(stub: ScTemplateBodyStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScTemplateBodyElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
