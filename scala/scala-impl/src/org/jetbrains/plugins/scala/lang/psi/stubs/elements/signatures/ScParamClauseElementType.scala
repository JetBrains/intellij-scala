package org.jetbrains.plugins.scala.lang.psi.stubs.elements.signatures

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterClauseImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType

final class ScParamClauseElementType extends ScStubElementType[ScParameterClause]("parameter clause") {
  override def createElement(node: ASTNode): ScParameterClause = new ScParameterClauseImpl(node)
}
