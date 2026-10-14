package org.jetbrains.plugins.scala.externalLibraries.kindProjector

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, TupleType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScMatchType, ScParameterizedType, TypePresentationContext}
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

import scala.collection.mutable

object KindProjectorSyntax {
  private val TupleN = "_root_.scala.Tuple(\\d+)".r
  private val FunctionN = "_root_.scala.Function(\\d+)".r

  private def boundsDefined(param: ScTypeParam): Boolean =
    param.lowerTypeElement.isDefined || param.upperTypeElement.isDefined

  private[kindProjector] def hasNoBounds(p: ScTypeParam): Boolean = {
    (p.lowerTypeElement, p.upperTypeElement) match {
      case (None, None) => true
      case _            => false
    }
  }

  private def tryConvertToInlineSyntax(alias: ScTypeAliasDefinition): Option[String] = {
    implicit val tpc: TypePresentationContext = TypePresentationContext(alias)
    implicit val context: Context = Context(alias)

    def simpleTypeArgumentOccurrences(tpe: ScParameterizedType): Map[String, Int] =
      tpe.typeArguments.collect { case tpt: TypeParameterType => tpt.name }
        .groupBy(identity)
        .view
        .mapValues(_.size)
        .toMap

    alias.aliasedType match {
      case Right(paramType: ScParameterizedType) =>
        val typeParams            = alias.typeParameters
        val validTypeParams       = typeParams.nonEmpty && typeParams.forall(hasNoBounds)
        val typeArgOccurrences    = simpleTypeArgumentOccurrences(paramType)
        val typeParamsAppearOnce  = typeParams.map(p => typeArgOccurrences.getOrElse(p.name, 0)).forall(_ == 1)

        if (validTypeParams && typeParamsAppearOnce) {
          val typeParamIt      = typeParams.iterator
          var currentTypeParam = Option(typeParamIt.next())

          val newTypeArgs = paramType.typeArguments.map { ta =>
            currentTypeParam match {
              case Some(tpt) if ta.presentableText == tpt.name =>
                currentTypeParam =
                  if (typeParamIt.hasNext) Option(typeParamIt.next())
                  else                     None
                tpt.getText.replace(tpt.name, KindProjectorUtil.placeholderSymbolFor(alias))
              case _ =>
                if (ScalaApplicationSettings.PRECISE_TEXT) ta.canonicalText
                else ta.presentableText
            }
          }

          paramType.designator.canonicalText match {
            case TupleN(n) if n != "1" =>
              Some(newTypeArgs.mkString("(", ", ", ")"))
            case FunctionN(n) if n != "0" =>
              val needsParentheses = n != "1" || paramType.typeArguments.headOption.exists {
                case FunctionType(_, _) => true
                case TupleType(_) => true
                case _: ScMatchType => true
                case _ => false
              }
              val args = if (needsParentheses) newTypeArgs.init.mkString("(", ", ", ")") else newTypeArgs.head
              Some(s"$args ${ScalaPsiUtil.functionArrow(using alias)} ${newTypeArgs.last}")
            case _ =>
              (!typeParamIt.hasNext && currentTypeParam.isEmpty).option {
                val designatorText =
                  if (ScalaApplicationSettings.PRECISE_TEXT) paramType.designator.canonicalText
                  else paramType.designator.presentableText
                s"$designatorText${newTypeArgs.mkString(start = "[", sep = ", ", end = "]")}"
              }
          }
        } else None
      case _ => None
    }
  }

  private def convertToFunctionSyntax(alias: ScTypeAliasDefinition): String = {
    implicit val tpc: TypePresentationContext = TypePresentationContext(alias)
    implicit val context: Context = Context(alias)

    val builder = new mutable.StringBuilder()

    // TODO Use standard type lambda presentation (as without -Ykind-projector), SCL-23275
    if (alias.isInScala3File) {
      builder ++= alias.typeParameters.map(_.getText).mkString("[", ", ", "]")
      builder ++= " =>> "
      builder ++= {
        val tpe = alias.aliasedType.getOrAny
        if (ScalaApplicationSettings.PRECISE_TEXT) tpe.canonicalText
        else tpe.presentableText
      }
    } else {
      val styleSettings = ScalaCodeStyleSettings.getInstance(alias.getProject)

      if (styleSettings.REPLACE_LAMBDA_WITH_GREEK_LETTER) builder ++= "λ"
      else builder ++= "Lambda"

      builder.append("[")
      val parameters = alias.typeParameters.map(param =>
        if (param.isCovariant || param.isContravariant || boundsDefined(param))
          s"`${param.getText}`"
        else param.getText
      )

      if (parameters.length > 1) builder ++= parameters.mkString(start = "(", sep = ", ", end = ")")
      else builder ++= parameters.mkString(start = "", sep = "", end = "")

      builder ++= " => "
      builder ++= alias.aliasedType.getOrAny.presentableText
      builder ++= "]"
    }

    builder.toString()
  }

  // TODO use TypePresentation's innerTypeText & NameRenderer, SCL-23282
  def convertToKindProjectorSyntax(alias: ScTypeAliasDefinition): String =
    tryConvertToInlineSyntax(alias).getOrElse(convertToFunctionSyntax(alias))
}
