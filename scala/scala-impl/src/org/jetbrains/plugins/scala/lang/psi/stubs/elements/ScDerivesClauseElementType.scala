package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScDerivesClause
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScDerivesClauseImpl

final class ScDerivesClauseElementType extends ScStubElementType[ScDerivesClause]("template derives") {
  override def createElement(node: ASTNode): ScDerivesClause = new ScDerivesClauseImpl(node)
}
