package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

class FunctionRenderer(
  typeParamsRenderer: TypeParamsRenderer,
  parametersRenderer: ParametersRenderer,
  typeAnnotationRenderer: TypeAnnotationRenderer,
  renderDefKeyword: Boolean
) {

  def render(function: ScFunction): String = {
    val keyword        = if (renderDefKeyword) "def " else ""
    val name           = function.name
    val typeParameters = typeParamsRenderer.renderParams(function)
    val parameters     = Option(function.paramClauses).map(parametersRenderer.renderClauses).getOrElse("")
    val typeAnnotation = typeAnnotationRenderer.render(function)
    s"$keyword$name$typeParameters$parameters$typeAnnotation"
  }
}

object FunctionRenderer {

  def simple(typeRenderer: TypeRenderer): FunctionRenderer = {
    val typeAnnotationRenderer = new TypeAnnotationRenderer(typeRenderer)
    new FunctionRenderer(
      new TypeParamsRenderer(typeRenderer),
      new ParametersRenderer(new ParameterRenderer(
        typeRenderer,
        ModifiersRenderer.SimpleText(),
        typeAnnotationRenderer
      )),
      typeAnnotationRenderer,
      false
    )
  }
}
