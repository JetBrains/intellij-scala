package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

/** 
* @author ilyas
*/

trait ScParameterOwner extends ScalaPsiElement {
  def parameters: collection.Seq[ScParameter]
  def clauses: Option[ScParameters]
  def allClauses: collection.Seq[ScParameterClause] = clauses match {
    case Some(x) => x.clauses
    case None => collection.Seq.empty
  }
}