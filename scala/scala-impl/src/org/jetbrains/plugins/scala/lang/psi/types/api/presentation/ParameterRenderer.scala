package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.ParameterRenderer.keywordPrefix

trait ParameterRendererLike {

  final def render(param: ScParameter): String = {
    val buffer = new StringBuilder()
    render(buffer, param)
    buffer.result()
  }

  def render(buffer: StringBuilder, param: ScParameter): Unit
}

class ParameterRenderer(
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

  def render(buffer: StringBuilder, param: ScParameter): Unit = {
    if (withAnnotations) parameterAnnotations(buffer, param)
    if (withMemberModifiers) {
      renderModifiers(buffer, param)
      buffer.append(keywordPrefix(param))
    } else {
      if (param.hasModifierProperty("inline")) {
        buffer.append("inline ")
      }
    }

    renderParameterNameAndType(buffer, param)
  }

  protected def renderParameterNameAndType(buffer: StringBuilder, param: ScParameter): Unit = {
    if (param.isAnonymousContextParameter) {
      //example: Int in `def f(using Int)`
      typeAnnotationRenderer.renderWithoutColon(buffer, param)
    } else {
      //example: `p: Int` in `def f(p: Int)`
      buffer.append(escaper.escape(param.name))
      typeAnnotationRenderer.render(buffer, param)
    }
  }

  protected def parameterAnnotations(buffer: StringBuilder, param: ScParameter): Unit = {
    val isMember = param match {
      case c: ScClassParameter => c.isClassMember
      case _                   => false
    }
    // When parameter is val, var, or case class val, annotations are related to member, not to parameter
    if (!isMember || withMemberModifiers)
      buffer.append(annotationsRenderer.renderAnnotations(param))
  }

  private def renderModifiers(buffer: StringBuilder, param: ScParameter): Unit =
    // do we really need this check? non class parameters will not contain modifiers anyway
    param match {
      case _: ScClassParameter => modifiersRenderer.render(buffer, param)
      case _                   =>
    }
}

object ParameterRenderer {

  def keywordPrefix(param: ScParameter) =
    param match {
      case c: ScClassParameter if c.isVal => "val "
      case c: ScClassParameter if c.isVar => "var "
      case _                              => ""
    }
}