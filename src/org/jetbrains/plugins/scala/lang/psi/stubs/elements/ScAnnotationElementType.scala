package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParenthesisedTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScAnnotationImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScAnnotationStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.06.2009
 */

class ScAnnotationElementType[Func <: ScAnnotation]
        extends ScStubElementType[ScAnnotationStub, ScAnnotation]("annotation") {
  def serialize(stub: ScAnnotationStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeName(stub.getTypeText)
  }

  def createPsi(stub: ScAnnotationStub): ScAnnotation = {
    new ScAnnotationImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScAnnotation, parentStub: StubElement[ParentPsi]): ScAnnotationStub = {
    val name = psi.typeElement match {
      case p: ScParenthesisedTypeElement => p.typeElement match {
        case Some(s: ScSimpleTypeElement) => s.reference match {
          case Some(ref: ScStableCodeReferenceElement) => ref.refName
          case _ => ""
        }
        case _ => ""
      }
      case s: ScSimpleTypeElement => s.reference match {
        case Some(ref) => ref.refName
        case _ => ""
      }
      case _ => ""
    }
    val typeText = psi.typeElement.getText
    new ScAnnotationStubImpl(parentStub, this, StringRef.fromString(name), StringRef.fromString(typeText))
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScAnnotationStub = {
    val name = dataStream.readName
    val typeText = dataStream.readName
    new ScAnnotationStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, name, typeText)
  }

  def indexStub(stub: ScAnnotationStub, sink: IndexSink): Unit = {

    val name = stub.getName
    if (name != null && name != "") {
      sink.occurrence(ScalaIndexKeys.ANNOTATED_MEMBER_KEY, name)
    }
  }
}