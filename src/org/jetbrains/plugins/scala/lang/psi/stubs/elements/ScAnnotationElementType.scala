package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import api.expr.ScAnnotation
import impl.{ScAnnotationStubImpl, ScEarlyDefinitionsStubImpl}
import psi.impl.expr.ScAnnotationImpl
import api.toplevel.ScEarlyDefinitions
import com.intellij.psi.stubs.{IndexSink, StubInputStream, StubElement, StubOutputStream}

import com.intellij.psi.PsiElement
import psi.impl.toplevel.ScEarlyDefinitionsImpl
import api.base.types.{ScSimpleTypeElement, ScParenthesisedTypeElement}
import api.base.ScStableCodeReferenceElement
import com.intellij.util.io.StringRef
import index.ScalaIndexKeys

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