package org.jetbrains.plugins.scala.editor.documentationProvider.renderers

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.{ParameterRenderer, ParametersRenderer, TextEscaper, TypeAnnotationRenderer, TypeRenderer}
import org.jetbrains.plugins.scala.editor.documentationProvider._
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.ParameterRenderer.keywordPrefix

final private [documentationProvider] class ScalaDocParametersRenderer(parameterRenderer: ParameterRenderer)
  extends ParametersRenderer(parameterRenderer, true) {

  override protected def renderImplicitOrUsingModifier(buffer: StringBuilder, clause: ScParameterClause, shouldRenderImplicitModifier: Boolean): Unit = {
    if (clause.isImplicit) {
      buffer.appendKeyword("implicit").append(" ")
    }

    //Always render `using` if it exists mostly to handle anonymous context parameters `(using Int)`
    //in order we don't end up in strange situation when we render just `(Int)`, which looks unclear without `using` prefix
    if (clause.isUsing) {
      buffer.appendKeyword("using").append(" ")
    }
  }
}

final private [documentationProvider] class ScalaDocParameterRenderer(typeRenderer: TypeRenderer, typeAnnotationRenderer: TypeAnnotationRenderer)
  extends ParameterRenderer(
    typeRenderer,
    WithHtmlPsiLink.modifiersRenderer,
    typeAnnotationRenderer,
    TextEscaper.Html,
    withMemberModifiers = true,
    withAnnotations = true
  ) {

  override def render(buffer: StringBuilder, param: ScParameter): Unit = {
    parameterAnnotations(buffer, param)
    WithHtmlPsiLink.modifiersRenderer.render(buffer, param)
    val keyword = keywordPrefix(param)
    if (keyword.nonEmpty) buffer.appendKeyword(keyword)

    renderParameterNameAndType(buffer, param)
  }
}

private [documentationProvider]  object ScalaDocParametersRenderer {
  def apply(typeRenderer: TypeRenderer, typeAnnotationRenderer: TypeAnnotationRenderer): ScalaDocParametersRenderer = {
    val parameterRenderer = new ScalaDocParameterRenderer(typeRenderer, typeAnnotationRenderer)
    new ScalaDocParametersRenderer(parameterRenderer)
  }
}