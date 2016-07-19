package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParenthesisedTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScAnnotationImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScAnnotationElementType.EMPTY_STRING_REF
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScAnnotationStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.ANNOTATED_MEMBER_KEY
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.cleanFqn

/**
  * User: Alexander Podkhalyuzin
  * Date: 22.06.2009
  */
class ScAnnotationElementType[Func <: ScAnnotation]
  extends ScStubElementType[ScAnnotationStub, ScAnnotation]("annotation") {
  override def serialize(stub: ScAnnotationStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.name)
    dataStream.writeName(stub.typeText)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScAnnotationStub =
    new ScAnnotationStubImpl(parentStub, this,
      nameRef = dataStream.readName, typeTextRef = dataStream.readName)

  override def createStub(psi: ScAnnotation, parentStub: StubElement[_ <: PsiElement]): ScAnnotationStub = {
    val maybeTypeElement = Option(psi) map {
      _.typeElement
    }

    val maybeName = maybeTypeElement flatMap {
      case parenthesised: ScParenthesisedTypeElement => parenthesised.typeElement
      case simple: ScSimpleTypeElement => Some(simple)
    } collect {
      case simple: ScSimpleTypeElement => simple
    } flatMap {
      _.reference
    } map {
      _.refName
    }

    val maybeTypeText = maybeTypeElement map {
      _.getText
    }

    def toStringRef(maybeString: Option[String]) = maybeString filter {
      !_.isEmpty
    } map {
      StringRef.fromString
    } getOrElse EMPTY_STRING_REF

    new ScAnnotationStubImpl(parentStub, this, toStringRef(maybeName), toStringRef(maybeTypeText))
  }

  override def indexStub(stub: ScAnnotationStub, sink: IndexSink): Unit = cleanFqn(stub.name) match {
    case null =>
    case "" =>
    case name => sink.occurrence(ANNOTATED_MEMBER_KEY, name)
  }

  override def createElement(node: ASTNode): ScAnnotation = new ScAnnotationImpl(node)

  override def createPsi(stub: ScAnnotationStub): ScAnnotation = new ScAnnotationImpl(stub)
}

object ScAnnotationElementType {
  private val EMPTY_STRING_REF = StringRef.fromString("")
}
