package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScParameterOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameters}


class ParametersRenderer(
  parameterRenderer: ParameterRendererLike,
  renderImplicitModifier: Boolean = false,
  clausesSeparator: String = "",
  paramsSeparator: String = ", "
) {

  def renderClauses(parameters: ScParameters): String = {
    val buffer = new StringBuilder()
    renderClauses(buffer, parameters.clauses)
    buffer.result()
  }

  def renderClauses(parametersOwner: ScParameterOwner): String = {
    val buffer = new StringBuilder()
    renderClauses(buffer, parametersOwner.allClauses)
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

  def renderClause(buffer: StringBuilder, clause: ScParameterClause): Unit = {
    val prefix = if (renderImplicitModifier && clause.isImplicit) "(implicit " else "("
    val suffix = ")"
    buffer.append(prefix)
    renderParameters(buffer, clause.parameters)
    buffer.append(suffix)
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