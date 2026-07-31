package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScAnnotationImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAnnotationStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScAnnotationElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScAnnotationStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys

final class ScAnnotationStubFactory(elementType: ScAnnotationElementType)
  extends ScStubSerializingElementFactory[ScAnnotationStub, ScAnnotation](elementType) {

  override def serialize(stub: ScAnnotationStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.annotationText)
    dataStream.writeOptionName(stub.name)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScAnnotationStub =
    new ScAnnotationStubImpl(parentStub, elementType, annotationText = dataStream.readNameString, name = dataStream.readOptionName)

  override def createStubImpl(annotation: ScAnnotation, parentStub: StubElement[_ <: PsiElement]): ScAnnotationStub =
    new ScAnnotationStubImpl(parentStub, elementType,
      annotationText = annotation.getText.stripPrefix("@"),
      name = annotation.constructorInvocation.reference.map(_.refName)
    )

  override def createPsi(stub: ScAnnotationStub): ScAnnotation = new ScAnnotationImpl(stub)

  override def indexStub(stub: ScAnnotationStub, sink: IndexSink): Unit = {
    sink.occurrences(ScalaIndexKeys.ANNOTATED_MEMBER_KEY, stub.name.toSeq: _*)
  }
}
