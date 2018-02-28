package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.codeInsight.hints.{Option => HintOption, _}
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

import scala.annotation.tailrec
import scala.collection.JavaConverters

private case object ParameterHintType extends HintType {

  override val options: Seq[HintOption] = Seq(
    HintOption(defaultValue = true, "parameter", "name")
  )

  override def apply(element: PsiElement): Seq[InlayInfo] = this {
    (element match {
      case _ if !options.head.get => Seq.empty
      case call: ScMethodCall if !call.isApplyOrUpdateCall => call.matchedParameters.reverse
      case call: ScConstructor => call.matchedParameters
      case _ => Seq.empty
    }).filter {
      case (argument, _) => element.isAncestorOf(argument)
    }
  }

  object methodInfo {

    import HintInfo.MethodInfo

    def unapply(element: PsiElement): Option[MethodInfo] = {
      val maybeReference = element match {
        case call: ScMethodCall => Some(call.getEffectiveInvokedExpr)
        case call: ScConstructor => call.reference
        case _ => None
      }

      maybeReference match {
        case Some(methodInfo(methodFQN, parametersNames)) =>
          import JavaConverters._
          Some(new MethodInfo(methodFQN, parametersNames.asJava))
        case _ => None
      }
    }

    private def unapply(element: ScalaPsiElement) = element match {
      case ResolvesTo(method: PsiMethod) =>
        val classFqn = method.containingClass match {
          case null => ""
          case clazz => s"${clazz.qualifiedName}."
        }

        val names = method.parameters.map(_.name)

        Some(classFqn + method.name, names)
      case _ => None
    }
  }

  private def apply(matchedParameters: Seq[(ScExpression, Parameter)]) =
    if (matchedParameters.nonEmpty) {
      val (varargs, regular) = matchedParameters.partition {
        case (_, parameter) => parameter.isRepeated
      }

      (regular ++ varargs.headOption).filter {
        case (argument, parameter) => isUnclear(argument) &&
          isNameable(argument) && isNameable(parameter)
      }.map {
        case (argument, parameter) => InlayInfo(parameter, argument)
      }
    } else Seq.empty

  @tailrec
  private def isUnclear(expression: ScExpression): Boolean = expression match {
    case _: ScLiteral | _: ScThisReference => true
    case ScParenthesisedExpr(inner) => isUnclear(inner)
    case ScSugarCallExpr(base, _, _) => isUnclear(base)
    case _ => false
  }

  private def isNameable(argument: ScExpression) =
    argument.getParent match {
      case list: ScArgumentExprList => !list.isBraceArgs
      case _ => false
    }

  private def isNameable(parameter: Parameter) =
    parameter.name.length > 1
}
