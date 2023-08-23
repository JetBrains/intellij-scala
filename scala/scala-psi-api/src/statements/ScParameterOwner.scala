package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedInUserData}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

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
    def effectiveParameterClauses: Seq[ScParameterClause] = cachedInUserData("effectiveParameterClauses", this, BlockModificationTracker(this)) {
      allClauses ++ clauses.flatMap(
        ScalaPsiUtil.syntheticParamClause(this, _, isClassParameter = false)()
      )
    }
  }
}