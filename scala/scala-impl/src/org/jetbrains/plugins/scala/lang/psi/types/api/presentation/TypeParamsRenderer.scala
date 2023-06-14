package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeBoundsOwner, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameterType, Variance}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil

class TypeParamsRenderer(
  typeRenderer: TypeRenderer,
  boundsRenderer: TypeBoundsRenderer = new TypeBoundsRenderer,
  stripContextTypeArgs: Boolean = false
) {

  def this(typeRenderer: TypeRenderer, textEscaper: TextEscaper, stripContextTypeArgs: Boolean) =
    this(typeRenderer, new TypeBoundsRenderer(textEscaper), stripContextTypeArgs)

  def renderParams(buffer: StringBuilder, paramsOwner: ScTypeParametersOwner): Unit =
    renderParams(buffer, paramsOwner.typeParameters)(render)
    
  final def renderParams(paramsOwner: ScTypeParametersOwner): String = {
    val buffer = new StringBuilder
    renderParams(buffer, paramsOwner.typeParameters)(render)
    buffer.result()
  }

  def render(param: ScTypeParam): String = {
    val buffer = new StringBuilder
    render(buffer, param)
    buffer.result()
  }

  def render(buffer: StringBuilder, param: ScTypeParam): Unit = {
    val parametersClauseRendered = param.typeParametersClause.fold("") { clause =>
      val sb = new StringBuilder
      render(sb, clause)
      sb.result()
    }
    renderImpl(
      buffer,
      param.name,
      param.variance,
      parametersClauseRendered,
      param.lowerBound.toOption,
      param.upperBound.toOption,
      param.viewBound,
      param.contextBound,
    )
  }

  def render(clause: ScTypeParamClause): String = {
    val buffer = new StringBuilder
    render(buffer, clause)
    buffer.result()
  }

  def render(buffer: StringBuilder, clause: ScTypeParamClause): Unit =
    renderParams(buffer, clause.typeParameters)(render)

  def render(buffer: StringBuilder, param: TypeParameterType): Unit = {
    val (viewTypes, contextTypes) = param.typeParameter.psiTypeParameter match {
      case boundsOwner: ScTypeBoundsOwner => (boundsOwner.viewBound, boundsOwner.contextBound)
      case _                              => TypeParamsRenderer.EmptyTuple
    }

    val parametersRendered = {
      val sb = new StringBuilder
      renderParams(sb, param.arguments)(render)
      sb.result()
    }
    renderImpl(
      buffer,
      param.name,
      param.variance,
      parametersRendered,
      Some(param.lowerType),
      Some(param.upperType),
      viewTypes,
      contextTypes,
    )
  }

  private def renderParams[T](buffer: StringBuilder, parameters: Seq[T])
                             (renderParam: (StringBuilder, T) => Unit): Unit =
    if (parameters.nonEmpty) {
      buffer.append("[")
      var isFirst = true
      parameters.foreach { p =>
        if (isFirst)
          isFirst = false
        else
          buffer.append(", ")
        renderParam(buffer, p)
      }
      buffer.append("]")
    }


  private def renderImpl(
    buffer: StringBuilder,
    paramName: String,
    variance: Variance,
    parametersClauseRendered: String, // for higher-kinded types case
    lower: Option[ScType],
    upper: Option[ScType],
    view: Seq[ScType],
    context: Seq[ScType]
  ): Unit = {
    val varianceText = variance match {
      case Variance.Contravariant => "-"
      case Variance.Covariant     => "+"
      case _                      => ""
    }
    buffer ++= varianceText
    buffer ++= paramName
    buffer ++= parametersClauseRendered

    lower.foreach { tp =>
      buffer.append(boundsRenderer.lowerBoundText(tp)(typeRenderer))
    }
    upper.foreach { tp =>
      buffer.append(boundsRenderer.upperBoundText(tp)(typeRenderer))
    }
    view.foreach { tp =>
      buffer.append(boundsRenderer.boundText(tp, ScalaTokenTypes.tVIEW)(typeRenderer))
    }
    context.foreach { tp =>
      val tpFixed = if (stripContextTypeArgs) ScTypeUtil.stripTypeArgs(tp) else tp
      val needsSpace = paramName.lastOption.exists(c => !c.isLetterOrDigit && c != '`')
      buffer.append(boundsRenderer.boundText(tpFixed, ScalaTokenTypes.tCOLON, space = needsSpace)(typeRenderer))
    }
  }
}

private object TypeParamsRenderer {
  private val EmptyTuple = (Seq.empty, Seq.empty)
}
