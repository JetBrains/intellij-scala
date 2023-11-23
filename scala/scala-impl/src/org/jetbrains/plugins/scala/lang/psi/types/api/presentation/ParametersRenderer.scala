package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScParameterOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameters}

class ParametersRenderer(
  parameterRenderer: ParameterRendererLike,
  shouldRenderImplicitModifier: Boolean,
  clausesSeparator: String = "",
  paramsSeparator: String = ", "
) {
  def renderClauses(parameters: ScParameters): String =
    renderClauses(parameters.clauses)

  def renderClauses(parametersOwner: ScParameterOwner): String =
    renderClauses(parametersOwner.allClauses)

  def renderClauses(clauses: Seq[ScParameterClause]): String = {
    val buffer = new StringBuilder()
    renderClauses(buffer, clauses)
    buffer.result()
  }

  def renderClauses(buffer: StringBuilder, clauses: Seq[ScParameterClause]): Unit =
    for (child <- clauses) {
      renderClause(buffer, child)
      buffer.append(clausesSeparator)
    }

  def renderClause(clause: ScParameterClause): String = {
    val buffer = new StringBuilder()
    renderClause(buffer, clause)
    buffer.result()
  }

  private def renderClause(buffer: StringBuilder, clause: ScParameterClause): Unit = {
    buffer.append("(")
    renderImplicitOrUsingModifier(buffer, clause, shouldRenderImplicitModifier)
    renderParameters(buffer, clause.parameters)
    buffer.append(")")
  }

  protected def renderImplicitOrUsingModifier(buffer: StringBuilder, clause: ScParameterClause, shouldRenderImplicitModifier: Boolean): Unit = {
    if (shouldRenderImplicitModifier && clause.isImplicit) {
      buffer.append("implicit ")
    }
    //Always render `using` if it exists mostly to handle anonymous context parameters `(using Int)`
    //in order we don't end up in strange situation when we render just `(Int)`, which looks unclear without `using` prefix
    if (clause.isUsing) {
      buffer.append("using ")
    }
  }

  private def renderParameters(buffer: StringBuilder, parameters: Seq[ScParameter]): Unit = {
    var isFirst = true
    for (param <- parameters) {
      if (isFirst)
        isFirst = false
      else
        buffer.append(paramsSeparator)
      parameterRenderer.render(buffer, param)
    }
  }
}
