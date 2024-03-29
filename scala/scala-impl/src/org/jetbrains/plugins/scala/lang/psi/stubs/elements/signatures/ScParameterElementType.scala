package org.jetbrains.plugins.scala.lang.psi.stubs.elements
package signatures

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParameterStub

class ScParameterElementType extends ScParamElementType[ScParameter]("parameter") {
  override def createElement(node: ASTNode): ScParameter = new ScParameterImpl(node)

  override def createPsi(stub: ScParameterStub): ScParameter = new ScParameterImpl(stub)
}