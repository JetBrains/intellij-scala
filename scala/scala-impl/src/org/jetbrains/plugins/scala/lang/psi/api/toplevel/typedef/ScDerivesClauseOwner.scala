package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScDerivesClause

trait ScDerivesClauseOwner extends ScTypeDefinition {
  def derivesClause: Option[ScDerivesClause] = extendsBlock.derivesClause
}
