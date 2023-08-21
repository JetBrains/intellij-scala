package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScParameterOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameters}

class ParametersRenderer(
  parameterRenderer: ParameterRendererLike,
  shouldRenderImplicitModifier: Boolean = false,
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
    if (shouldRenderImplicitModifier) renderImplicitModifier(buffer, clause)
    renderParameters(buffer, clause.parameters)
    buffer.append(")")
  }

  protected def renderImplicitModifier(buffer: StringBuilder, clause: ScParameterClause): Unit =
    if (clause.isImplicit) buffer.append("implicit ")
    else if (clause.isUsing) buffer.append("using ")

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
