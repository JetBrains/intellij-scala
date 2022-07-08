package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScAnnotationImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScAnnotationStubImpl

final class ScAnnotationElementType extends ScStubElementType[ScAnnotationStub, ScAnnotation]("annotation") {
  override def serialize(stub: ScAnnotationStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.annotationText)
    dataStream.writeOptionName(stub.name)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScAnnotationStub =
    new ScAnnotationStubImpl(parentStub, this, annotationText = dataStream.readNameString, name = dataStream.readOptionName)

  override def createStubImpl(annotation: ScAnnotation, parentStub: StubElement[_ <: PsiElement]): ScAnnotationStub = {
    new ScAnnotationStubImpl(parentStub, this,
      annotationText = annotation.getText.stripPrefix("@"),
      name = annotation.constructorInvocation.reference.map(_.refName)
    )
  }

  override def indexStub(stub: ScAnnotationStub, sink: IndexSink): Unit = {
    sink.occurrences(index.ScalaIndexKeys.ANNOTATED_MEMBER_KEY, stub.name.toSeq: _*)
  }

  override def createElement(node: ASTNode): ScAnnotation = new ScAnnotationImpl(node)

  override def createPsi(stub: ScAnnotationStub): ScAnnotation = new ScAnnotationImpl(stub)
}
