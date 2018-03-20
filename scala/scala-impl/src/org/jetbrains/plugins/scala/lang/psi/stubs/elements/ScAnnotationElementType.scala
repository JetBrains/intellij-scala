package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParenthesisedTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScAnnotationImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScAnnotationStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.ANNOTATED_MEMBER_KEY

/**
  * User: Alexander Podkhalyuzin
  * Date: 22.06.2009
  */
class ScAnnotationElementType extends ScStubElementType[ScAnnotationStub, ScAnnotation]("annotation") {
  override def serialize(stub: ScAnnotationStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeOptionName(stub.name)
    dataStream.writeOptionName(stub.typeText)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScAnnotationStub =
    new ScAnnotationStubImpl(parentStub, this,
      nameRef = dataStream.readOptionName,
      typeTextRef = dataStream.readOptionName)

  override def createStubImpl(annotation: ScAnnotation, parentStub: StubElement[_ <: PsiElement]): ScAnnotationStub = {
    val maybeTypeElement = Option(annotation).map {
      _.typeElement
    }

    val maybeName = maybeTypeElement.flatMap {
      case parenthesised: ScParenthesisedTypeElement => parenthesised.innerElement
      case simple: ScSimpleTypeElement => Some(simple)
      case _ => None
    }.collect {
      case simple: ScSimpleTypeElement => simple
    }.flatMap {
      _.reference
    }.map {
      _.refName
    }

    val maybeTypeText = maybeTypeElement.map {
      _.getText
    }

    new ScAnnotationStubImpl(parentStub, this,
      nameRef = maybeName.asReference,
      typeTextRef = maybeTypeText.asReference)
  }

  override def indexStub(stub: ScAnnotationStub, sink: IndexSink): Unit =
    this.indexStub(stub.name.toArray, sink, ANNOTATED_MEMBER_KEY)

  override def createElement(node: ASTNode): ScAnnotation = new ScAnnotationImpl(node)

  override def createPsi(stub: ScAnnotationStub): ScAnnotation = new ScAnnotationImpl(stub)
}
