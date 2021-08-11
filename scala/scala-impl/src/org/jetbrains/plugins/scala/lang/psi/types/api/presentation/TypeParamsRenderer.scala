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

  def renderParams(paramsOwner: ScTypeParametersOwner): String =
    renderParams(paramsOwner.typeParameters)(render)

  def render(param: ScTypeParam): String = {
    val parametersClauseRendered = param.typeParametersClause.fold("")(render)
    renderImpl(
      param.name,
      param.variance,
      parametersClauseRendered,
      param.lowerBound.toOption,
      param.upperBound.toOption,
      param.viewBound,
      param.contextBound,
    )
  }

  def render(clause: ScTypeParamClause): String =
    renderParams(clause.typeParameters)(render)

  def render(param: TypeParameterType): String = {
    val (viewTypes, contextTypes) = param.typeParameter.psiTypeParameter match {
      case boundsOwner: ScTypeBoundsOwner => (boundsOwner.viewBound, boundsOwner.contextBound)
      case _                              => TypeParamsRenderer.EmptyTuple
    }

    val parametersRendered = renderParams(param.arguments)(render)
    renderImpl(
      param.name,
      param.variance,
      parametersRendered,
      Some(param.lowerType),
      Some(param.upperType),
      viewTypes,
      contextTypes,
    )
  }

  private def renderParams[T](parameters: Seq[T])
                             (renderParam: T => String): String =
    if (parameters.isEmpty) "" else {
      val buffer = new StringBuilder

      if (parameters.nonEmpty) {
        buffer.append("[")
        var isFirst = true
        parameters.foreach { p =>
          if (isFirst)
            isFirst = false
          else
            buffer.append(", ")
          val paramRendered = renderParam(p)
          buffer.append(paramRendered)
        }
        buffer.append("]")
      }

      buffer.result()
    }

  private def renderImpl(
    paramName: String,
    variance: Variance,
    parametersClauseRendered: String, // for higher-kinded types case
    lower: Option[ScType],
    upper: Option[ScType],
    view: Seq[ScType],
    context: Seq[ScType]
  ): String = {
    val buffer = new StringBuilder

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
      buffer.append(boundsRenderer.boundText(tpFixed, ScalaTokenTypes.tCOLON)(typeRenderer))
    }

    buffer.result()
  }
}


private object TypeParamsRenderer {
  private val EmptyTuple = (Seq.empty, Seq.empty)
}