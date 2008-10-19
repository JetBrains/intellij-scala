package org.jetbrains.plugins.scala.lang.psi.stubs.elements.signatures

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScClassParameterImpl
import api.statements.params.{ScClassParameter, ScParameter}
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.util.io.StringRef
import impl.{ScParameterStubImpl}

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

class ScClassParameterElementType extends ScParamElementType[ScClassParameter]("class parameter") {

  def createPsi(stub: ScParameterStub): ScClassParameter = {
    new ScClassParameterImpl(stub)
  }
}