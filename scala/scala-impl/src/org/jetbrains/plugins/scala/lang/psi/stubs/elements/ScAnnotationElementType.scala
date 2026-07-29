package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScAnnotationImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAnnotationStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScAnnotationStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys

final class ScAnnotationElementType extends ScalaStubBasedElementType[ScAnnotationStub, ScAnnotation](ScAnnotationElementType.DebugName) {
  override def createElement(node: ASTNode): ScAnnotation = new ScAnnotationImpl(node)
}

object ScAnnotationElementType {
  val DebugName = "annotation"
}

class ScAnnotationStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScAnnotationStub, ScAnnotation] {

  override def serialize(stub: ScAnnotationStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.annotationText)
    dataStream.writeOptionName(stub.name)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScAnnotationStub =
    new ScAnnotationStubImpl(parentStub, elementType, annotationText = dataStream.readNameString, name = dataStream.readOptionName)

  override def createStub(annotation: ScAnnotation, parentStub: StubElement[_ <: PsiElement]): ScAnnotationStub =
    ScStubElementType.Processing.run {
      new ScAnnotationStubImpl(parentStub, elementType,
        annotationText = annotation.getText.stripPrefix("@"),
        name = annotation.constructorInvocation.reference.map(_.refName))
    }

  override def createPsi(stub: ScAnnotationStub): ScAnnotation = new ScAnnotationImpl(stub)

  override def indexStub(stub: ScAnnotationStub, sink: IndexSink): Unit = {
    sink.occurrences(ScalaIndexKeys.ANNOTATED_MEMBER_KEY, stub.name.toSeq: _*)
  }

  override def getExternalId: String = s"scala.${ScAnnotationElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
