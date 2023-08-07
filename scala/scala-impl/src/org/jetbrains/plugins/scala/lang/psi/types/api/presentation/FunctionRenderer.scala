package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

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
    typeParamsRenderer.foreach(_.renderParams(buffer, function))
    Option(function.paramClauses).foreach(params => parametersRenderer.renderClauses(buffer, params.clauses))
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
