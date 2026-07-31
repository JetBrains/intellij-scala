package org.jetbrains.plugins.scala.lang.psi.stubs.elements.signatures

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterImpl

final class ScParameterElementType extends ScParamElementType[ScParameter]("parameter") {
  override def createElement(node: ASTNode): ScParameter = new ScParameterImpl(node)
}
