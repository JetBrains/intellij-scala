package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScParameterOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameters}


class ParametersRenderer(
  parameterRenderer: ParameterRendererLike,
  renderImplicitModifier: Boolean = false,
  clausesSeparator: String = "",
  paramsSeparator: String = ", "
) {

  def renderClauses(parameters: ScParameters): String =
    renderClauses(parameters.clauses)

  def renderClauses(parametersOwner: ScParameterOwner): String =
    renderClauses(parametersOwner.allClauses)

  def renderClauses(clauses: Seq[ScParameterClause]): String = {
    val buffer = new StringBuilder()
    for (child <- clauses) {
      renderClause(child, buffer)
      buffer.append(clausesSeparator)
    }
    buffer.result()
  }

  def renderClause(clause: ScParameterClause): String = {
    val buffer = new StringBuilder()
    renderClause(clause, buffer)
    buffer.result()
  }

  def renderClause(clause: ScParameterClause, buffer: StringBuilder): Unit = {
    val prefix = if (renderImplicitModifier && clause.isImplicit) "(implicit " else "("
    val suffix = ")"
    buffer.append(prefix)
    renderParameters(clause.parameters, buffer)
    buffer.append(suffix)
  }

  private def renderParameters(parameters: Seq[ScParameter], buffer: StringBuilder): Unit = {
    var isFirst = true
    for (param <- parameters) {
      if (isFirst)
        isFirst = false
      else
        buffer.append(paramsSeparator)
      parameterRenderer.render(param, buffer)
    }
  }
}