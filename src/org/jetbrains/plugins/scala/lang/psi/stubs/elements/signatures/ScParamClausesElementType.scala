package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements
package signatures

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParametersImpl
import api.statements.params.ScParameters
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import impl.ScParamClausesStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

class ScParamClausesElementType
extends ScStubElementType[ScParamClausesStub, ScParameters]("parameter clauses") {
  def serialize(stub: ScParamClausesStub, dataStream: StubOutputStream) {}

  def indexStub(stub: ScParamClausesStub, sink: IndexSink) {}

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScParamClausesStub = {
    new ScParamClausesStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScParameters, parentStub: StubElement[ParentPsi]) = {
    new ScParamClausesStubImpl(parentStub, this)     
  }

  def createPsi(stub: ScParamClausesStub): ScParameters = {
    new ScParametersImpl(stub)
  }
}