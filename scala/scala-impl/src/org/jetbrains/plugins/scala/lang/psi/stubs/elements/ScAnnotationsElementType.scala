package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotations
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScAnnotationsImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAnnotationsStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScAnnotationsStubImpl

class ScAnnotationsElementType extends ScalaStubBasedElementType[ScAnnotationsStub, ScAnnotations](ScAnnotationElementType.DebugName) {
  override def createElement(node: ASTNode): ScAnnotations = new ScAnnotationsImpl(node)
}

object ScAnnotationsElementType {
  val DebugName = "annotations"
}

class ScAnnotationsStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScAnnotationsStub, ScAnnotations] {
  override def serialize(stub: ScAnnotationsStub, dataStream: StubOutputStream): Unit = {}

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScAnnotationsStub =
    new ScAnnotationsStubImpl(parentStub, elementType)

  override def createStub(psi: ScAnnotations, parentStub: StubElement[_ <: PsiElement]): ScAnnotationsStub =
    ScStubElementType.Processing.run {
      new ScAnnotationsStubImpl(parentStub, elementType)
    }

  override def createPsi(stub: ScAnnotationsStub): ScAnnotations = new ScAnnotationsImpl(stub)

  override def indexStub(stub: ScAnnotationsStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScAnnotationElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
