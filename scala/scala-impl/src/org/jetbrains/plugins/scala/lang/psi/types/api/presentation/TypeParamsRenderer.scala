package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiPresentationUtils.TypeRenderer
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeBoundsOwner, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameterType, Variance}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil

class TypeParamsRenderer(
  boundsRenderer: TypeBoundsRenderer = new TypeBoundsRenderer,
  stripContextTypeArgs: Boolean = false
) {

  def this(textEscaper: TextEscaper, stripContextTypeArgs: Boolean) =
    this(new TypeBoundsRenderer(textEscaper), stripContextTypeArgs)

  def render(paramsOwner: ScTypeParametersOwner)
            (typeRenderer: TypeRenderer): String =
    renderParams(paramsOwner.typeParameters)(render(_)(typeRenderer))

  def render(param: ScTypeParam)
            (typeRenderer: TypeRenderer): String = {
    val parametersClauseRendered = param.typeParametersClause.fold("") { clause =>
      renderParams(clause.typeParameters)(render(_)(typeRenderer))
    }
    renderImpl(
      param.name,
      param.variance,
      parametersClauseRendered,
      param.lowerBound.toOption,
      param.upperBound.toOption,
      param.viewBound,
      param.contextBound,
    )(typeRenderer)
  }

  def render(param: TypeParameterType)
            (typeRenderer: TypeRenderer): String = {
    val (viewTypes, contextTypes) = param.typeParameter.psiTypeParameter match {
      case boundsOwner: ScTypeBoundsOwner => (boundsOwner.viewBound, boundsOwner.contextBound)
      case _                              => TypeParamsRenderer.EmptyTuple
    }

    val parametersRendered = renderParams(param.arguments)(render(_)(typeRenderer))
    renderImpl(
      param.name,
      param.variance,
      parametersRendered,
      Some(param.lowerType),
      Some(param.upperType),
      viewTypes,
      contextTypes,
    )(typeRenderer)
  }

  private def renderParams[T](parameters: Seq[T])
                             (renderParam: T => String): String =
    if (parameters.isEmpty) "" else {
      val buffer = StringBuilder.newBuilder

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

      buffer.result
    }

  private def renderImpl(
    paramName: String,
    variance: Variance,
    parametersClauseRendered: String, // for higher-kinded types case
    lower: Option[ScType],
    upper: Option[ScType],
    view: Seq[ScType],
    context: Seq[ScType]
  )(typeRenderer: TypeRenderer): String = {
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

    buffer.result
  }
}


private object TypeParamsRenderer {
  private val EmptyTuple = (Seq.empty, Seq.empty)
}