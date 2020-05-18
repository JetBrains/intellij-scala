package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiPresentationUtils.TypeRenderer
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameterType, Variance}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil

final class TypeBoundsRenderer(
  textEscaper: TextEscaper = TextEscaper.Noop,
  stripContextTypeArgs: Boolean = false
) {

  import ScalaTokenTypes.{tLOWER_BOUND, tUPPER_BOUND}

  def upperBoundText(typ: ScType)
                    (toString: TypeRenderer): String =
    if (typ.isAny) ""
    else boundText(typ, tUPPER_BOUND)(toString)

  def lowerBoundText(typ: ScType)
                    (toString: TypeRenderer): String =
    if (typ.isNothing) ""
    else boundText(typ, tLOWER_BOUND)(toString)

  def boundText(typ: ScType, bound: IElementType)
               (toString: TypeRenderer): String = {
    val boundEscaped = textEscaper.escape(bound.toString)
    " " + boundEscaped + " " + toString(typ)
  }

  def renderClause(typeParamClause: ScTypeParamClause)
                  (toString: TypeRenderer): String =
    renderParams(typeParamClause.typeParameters)(render(_)(toString))

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

  // TODO: this should go to some type param renderer
  def render(param: ScTypeParam)
            (toString: TypeRenderer): String = {
    val parametersClauseRendered = param.typeParametersClause.fold("")(renderClause(_)(toString))
    renderImpl(
      param.name,
      param.variance,
      parametersClauseRendered,
      param.lowerBound.toOption,
      param.upperBound.toOption,
      param.viewBound,
      param.contextBound,
    )(toString)
  }

  def render(param: TypeParameterType)
            (toString: TypeRenderer): String = {
    val (viewTypes, contextTypes) = param.typeParameter.psiTypeParameter match {
      case boundsOwner: ScTypeBoundsOwner => (boundsOwner.viewBound, boundsOwner.contextBound)
      case _                              => TypeBoundsRenderer.EmptyTuple
    }

    val parametersRendered = renderParams(param.arguments)(render(_)(toString))
    renderImpl(
      param.name,
      param.variance,
      parametersRendered,
      Some(param.lowerType),
      Some(param.upperType),
      viewTypes,
      contextTypes,
    )(toString)
  }

  private def renderImpl(
    paramName: String,
    variance: Variance,
    parametersClauseRendered: String, // for higher-kinded types case
    lower: Option[ScType],
    upper: Option[ScType],
    view: Seq[ScType],
    context: Seq[ScType]
  )(toString: TypeRenderer): String = {
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
      buffer.append(lowerBoundText(tp)(toString))
    }
    upper.foreach { tp =>
      buffer.append(upperBoundText(tp)(toString))
    }
    view.foreach { tp =>
      buffer.append(boundText(tp, ScalaTokenTypes.tVIEW)(toString))
    }
    context.foreach { tp =>
      val tpFixed = if (stripContextTypeArgs) ScTypeUtil.stripTypeArgs(tp) else tp
      buffer.append(boundText(tpFixed, ScalaTokenTypes.tCOLON)(toString))
    }

    buffer.result
  }
}

private object TypeBoundsRenderer {
  private val EmptyTuple = (Seq.empty, Seq.empty)
}