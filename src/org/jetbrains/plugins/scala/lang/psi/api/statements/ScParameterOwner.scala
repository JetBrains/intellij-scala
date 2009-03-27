package org.jetbrains.plugins.scala.lang.psi.api.statements

import impl.statements.params.ScParameterClauseImpl
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

/** 
* @author ilyas
*/

trait ScParameterOwner extends ScalaPsiElement {
  def parameters: Seq[ScParameter]
  def clauses: Option[ScParameters]
  def allClauses: Seq[ScParameterClause] = clauses match {
    case Some(x) => x.clauses
    case None => Seq.empty
  }
}