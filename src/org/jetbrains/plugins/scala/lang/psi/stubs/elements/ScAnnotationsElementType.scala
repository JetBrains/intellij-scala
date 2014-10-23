package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotations
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScAnnotationsImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScAnnotationsStubImpl
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