package org.jetbrains.plugins.scala.editor.documentationProvider.renderers

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.{ParameterRenderer, ParametersRenderer, TextEscaper, TypeAnnotationRenderer, TypeRenderer}
import org.jetbrains.plugins.scala.editor.documentationProvider._

final private [documentationProvider] class ScalaDocParametersRenderer(parameterRenderer: ParameterRenderer)
  extends ParametersRenderer(parameterRenderer, true, "", "") {

  override protected def renderImplicitModifier(buffer: StringBuilder, clause: ScParameterClause): Unit =
    if (clause.isImplicit) buffer.appendKeyword("implicit").append(" ")
    else if (clause.isUsing) buffer.appendKeyword("using").append(" ")
}

private [documentationProvider]  object ScalaDocParametersRenderer {
  def apply(typeRenderer: TypeRenderer, typeAnnotationRenderer: TypeAnnotationRenderer): ScalaDocParametersRenderer = {
    val parameterRenderer = new ParameterRenderer(
      typeRenderer,
      WithHtmlPsiLink.renderer,
      typeAnnotationRenderer,
      TextEscaper.Html,
      withMemberModifiers = false,
      withAnnotations = true
    )
    new ScalaDocParametersRenderer(parameterRenderer)
  }
}