package org.jetbrains.plugins.scala.lang.psi.stubs.elements


import api.expr.ScAnnotation
import impl.{ScAnnotationStubImpl, ScEarlyDefinitionsStubImpl}
import psi.impl.expr.ScAnnotationImpl
import api.toplevel.ScEarlyDefinitions
import com.intellij.psi.stubs.{IndexSink, StubInputStream, StubElement, StubOutputStream}

import com.intellij.psi.PsiElement
import psi.impl.toplevel.ScEarlyDefinitionsImpl
/**
 * User: Alexander Podkhalyuzin
 * Date: 22.06.2009
 */

class ScAnnotationElementType[Func <: ScAnnotation]
        extends ScStubElementType[ScAnnotationStub, ScAnnotation]("annotation") {
  def serialize(stub: ScAnnotationStub, dataStream: StubOutputStream): Unit = {
  }

  def createPsi(stub: ScAnnotationStub): ScAnnotation = {
    new ScAnnotationImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScAnnotation, parentStub: StubElement[ParentPsi]): ScAnnotationStub = {
    new ScAnnotationStubImpl(parentStub, this)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScAnnotationStub = {
    new ScAnnotationStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)
  }

  def indexStub(stub: ScAnnotationStub, sink: IndexSink): Unit = {}
}