package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScTypeParamClauseImpl

final class ScTypeParamClauseElementType extends ScStubElementType[ScTypeParamClause]("type parameter clause") {
  override def createElement(node: ASTNode): ScTypeParamClause = new ScTypeParamClauseImpl(node)
}
