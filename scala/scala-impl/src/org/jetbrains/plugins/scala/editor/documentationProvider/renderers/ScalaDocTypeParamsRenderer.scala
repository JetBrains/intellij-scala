package org.jetbrains.plugins.scala.editor.documentationProvider.renderers

import org.jetbrains.plugins.scala.editor.documentationProvider.HtmlBuilderWrapper
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.lang.psi.types.api.Variance
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TextEscaper.Html
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.{TypeBoundsRenderer, TypeParamsRenderer, TypeRenderer}

final private [documentationProvider] class ScalaDocTypeParamsRenderer(typeRenderer: TypeRenderer)
  extends TypeParamsRenderer(typeRenderer, new TypeBoundsRenderer(Html)) {
  override protected def renderParamName(buffer: StringBuilder, paramName: String, variance: Variance): Unit = {
    val nameToRender = variance match {
      case Variance.Contravariant => s"-$paramName"
      case Variance.Covariant => s"+$paramName"
      case _ => paramName
    }
    buffer.appendAs(nameToRender, DefaultHighlighter.TYPEPARAM)
  }
}
