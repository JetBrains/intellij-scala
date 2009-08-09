package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import api.expr.ScAnnotations
import impl.{ScAnnotationsStubImpl, ScEarlyDefinitionsStubImpl}
import psi.impl.expr.ScAnnotationsImpl
import api.toplevel.ScEarlyDefinitions
import com.intellij.psi.stubs.{IndexSink, StubInputStream, StubElement, StubOutputStream}
import com.intellij.psi.PsiElement
import psi.impl.toplevel.ScEarlyDefinitionsImpl
/**
 * User: Alexander Podkhalyuzin
 * Date: 22.06.2009
 */

class ScAnnotationsElementType[Func <: ScAnnotations]
        extends ScStubElementType[ScAnnotationsStub, ScAnnotations]("annotations") {
  def serialize(stub: ScAnnotationsStub, dataStream: StubOutputStream): Unit = {
  }

  def createPsi(stub: ScAnnotationsStub): ScAnnotations = {
    new ScAnnotationsImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScAnnotations, parentStub: StubElement[ParentPsi]): ScAnnotationsStub = {
    new ScAnnotationsStubImpl(parentStub, this)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScAnnotationsStub = {
    new ScAnnotationsStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)
  }

  def indexStub(stub: ScAnnotationsStub, sink: IndexSink): Unit = {}
}