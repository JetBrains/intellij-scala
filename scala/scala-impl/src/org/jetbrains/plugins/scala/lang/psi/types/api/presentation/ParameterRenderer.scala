package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.ParameterRenderer.keywordPrefix

trait ParameterRendererLike {

  final def render(param: ScParameter): String = {
    val builder = new StringBuilder()
    render(param, builder)
    builder.result()
  }

  def render(param: ScParameter, buffer: StringBuilder): Unit
}

final class ParameterRenderer(
  typeRenderer: TypeRenderer,
  modifiersRenderer: ModifiersRendererLike, // only makes sense for class constructor parameters which declare fields at the same time
  typeAnnotationRenderer: TypeAnnotationRenderer,
  escaper: TextEscaper = TextEscaper.Noop,
  withMemberModifiers: Boolean = false, // e.g. for constructor parameters
  withAnnotations: Boolean = false
) extends ParameterRendererLike {

  def this(
    typeRenderer: TypeRenderer,
    modifiersRenderer: ModifiersRendererLike,
    escaper: TextEscaper,
    withMemberModifiers: Boolean,
    withAnnotations: Boolean
  ) = this(
    typeRenderer,
    modifiersRenderer,
    new TypeAnnotationRenderer(typeRenderer),
    escaper,
    withMemberModifiers, withAnnotations
  )

  def this(
    typeRenderer: TypeRenderer,
    modifiersRenderer: ModifiersRendererLike
  ) = this(
    typeRenderer,
    modifiersRenderer,
    TextEscaper.Noop, false, false
  )

  private def annotationsRenderer = new AnnotationsRenderer(typeRenderer, separator = " ", escaper)

  def render(param: ScParameter, buffer: StringBuilder): Unit = {
    if (withAnnotations) {
      buffer.append(parameterAnnotations(param))
    }
    if (withMemberModifiers) {
      buffer.append(renderModifiers(param))
      buffer.append(keywordPrefix(param))
    }
    buffer.append(escaper.escape(param.name))
    buffer.append(typeAnnotationRenderer.render(param))
  }

  private def parameterAnnotations(param: ScParameter): String = {
    val isMember = param match {
      case c: ScClassParameter => c.isClassMember
      case _                   => false
    }
    // When parameter is val, var, or case class val, annotations are related to member, not to parameter
    if (isMember && !withMemberModifiers) "" else {
      annotationsRenderer.renderAnnotations(param)
    }
  }

  private def renderModifiers(param: ScParameter): String =
    // do we really need this check? non class parameters will not contain modifiers anyway
    param match {
      case _: ScClassParameter => modifiersRenderer.render(param)
      case _                   => ""
    }
}

object ParameterRenderer {

  private def keywordPrefix(param: ScParameter) =
    param match {
      case c: ScClassParameter if c.isVal => "val "
      case c: ScClassParameter if c.isVar => "var "
      case _                              => ""
    }
}