package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScSignatureClause

class FunctionRenderer(
  typeParamsRenderer: Option[TypeParamsRenderer],
  parametersRenderer: ParametersRenderer,
  typeAnnotationRenderer: TypeAnnotationRenderer,
  renderDefKeyword: Boolean
) {

  def this(
    typeParamsRenderer: TypeParamsRenderer,
    parametersRenderer: ParametersRenderer,
    typeAnnotationRenderer: TypeAnnotationRenderer,
    renderDefKeyword: Boolean
  ) = this(Some(typeParamsRenderer), parametersRenderer, typeAnnotationRenderer, renderDefKeyword)

  def render(function: ScFunction): String = {
    val buffer = new StringBuilder
    if (renderDefKeyword) buffer.append("def ")
    buffer.append(function.name)
    function.signatureClauses.foreach {
      case ScSignatureClause.TypeClause(clause) =>
        typeParamsRenderer.foreach(renderer => buffer.append(renderer.render(clause)))
      case ScSignatureClause.TermClause(clause) =>
        buffer.append(parametersRenderer.renderClause(clause))
    }
    typeAnnotationRenderer.render(buffer, function)
    buffer.result()
  }
}

object FunctionRenderer {

  def simple(typeRenderer: TypeRenderer): FunctionRenderer = {
    val typeAnnotationRenderer = new TypeAnnotationRenderer(typeRenderer)
    val parameterRenderer = new ParameterRenderer(
      typeRenderer,
      ModifiersRenderer.SimpleText(),
      typeAnnotationRenderer
    )
    new FunctionRenderer(
      new TypeParamsRenderer(typeRenderer),
      new ParametersRenderer(parameterRenderer, shouldRenderImplicitModifier = false),
      typeAnnotationRenderer,
      renderDefKeyword = false
    )
  }
}
