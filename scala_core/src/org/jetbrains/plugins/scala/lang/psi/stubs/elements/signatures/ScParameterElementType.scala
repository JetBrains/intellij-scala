package org.jetbrains.plugins.scala.lang.psi.stubs.elements.signatures

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterImpl
import api.statements.params.ScParameter
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.util.io.StringRef
import impl.ScParameterStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

class ScParameterElementType extends ScParamElementType[ScParameter]("parameter") {


  def createPsi(stub: ScParameterStub): ScParameter = {
    new ScParameterImpl(stub)
  }
}