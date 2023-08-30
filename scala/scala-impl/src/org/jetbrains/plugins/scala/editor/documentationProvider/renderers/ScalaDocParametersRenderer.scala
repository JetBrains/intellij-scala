package org.jetbrains.plugins.scala.editor.documentationProvider.renderers

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.{ParameterRenderer, ParametersRenderer, TextEscaper, TypeAnnotationRenderer, TypeRenderer}
import org.jetbrains.plugins.scala.editor.documentationProvider._
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.ParameterRenderer.keywordPrefix

final private [documentationProvider] class ScalaDocParametersRenderer(parameterRenderer: ParameterRenderer)
  extends ParametersRenderer(parameterRenderer, true) {

  override protected def renderImplicitModifier(buffer: StringBuilder, clause: ScParameterClause): Unit =
    if (clause.isImplicit) buffer.appendKeyword("implicit").append(" ")
    else if (clause.isUsing) buffer.appendKeyword("using").append(" ")
}

final private [documentationProvider] class ScalaDocParameterRenderer(typeRenderer: TypeRenderer, typeAnnotationRenderer: TypeAnnotationRenderer)
  extends ParameterRenderer(
    typeRenderer,
    WithHtmlPsiLink.renderer,
    typeAnnotationRenderer,
    TextEscaper.Html,
    withMemberModifiers = true,
    withAnnotations = true
  ) {

  override def render(buffer: StringBuilder, param: ScParameter): Unit = {
    parameterAnnotations(buffer, param)
    WithHtmlPsiLink.renderer.render(buffer, param)
    buffer.appendKeyword(keywordPrefix(param))
    buffer.append(TextEscaper.Html.escape(param.name))
    typeAnnotationRenderer.render(buffer, param)
  }
}

private [documentationProvider]  object ScalaDocParametersRenderer {
  def apply(typeRenderer: TypeRenderer, typeAnnotationRenderer: TypeAnnotationRenderer): ScalaDocParametersRenderer = {
    val parameterRenderer = new ScalaDocParameterRenderer(typeRenderer, typeAnnotationRenderer)
    new ScalaDocParametersRenderer(parameterRenderer)
  }
}