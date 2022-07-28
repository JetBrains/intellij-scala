package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

trait ScParameterOwner extends ScalaPsiElement {
  def parameters: Seq[ScParameter]
  def clauses: Option[ScParameters]
  def allClauses: Seq[ScParameterClause] = clauses match {
    case Some(x) => x.clauses
    case None => Seq.empty
  }
}

object ScParameterOwner {
  trait WithContextBounds extends ScParameterOwner with ScTypeParametersOwner {
    @CachedInUserData(this, BlockModificationTracker(this))
    def effectiveParameterClauses: Seq[ScParameterClause] =
      allClauses ++ clauses.flatMap(
        ScalaPsiUtil.syntheticParamClause(this, _, isClassParameter = false)()
      )
  }
}