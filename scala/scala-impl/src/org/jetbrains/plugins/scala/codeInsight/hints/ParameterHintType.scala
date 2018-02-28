package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.codeInsight.hints.{Option => HintOption, _}
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec
import scala.collection.JavaConverters

private case object ParameterHintType extends HintType {

  import ScFunction.Ext.{Apply, GetSet, Update}

  private[hints] val parameterNames = HintOption("parameter", "name")(defaultValue = true)
  private[hints] val applyUpdateParameterNames = HintOption(Apply, Update)(name = s"Show parameter name hints for `$Apply`, `$Update` methods")

  override val options: Seq[HintOption] = Seq(
    parameterNames,
    applyUpdateParameterNames,
  )

  override def apply(element: PsiElement): Seq[InlayInfo] = this {
    (element match {
      case _ if !parameterNames.get => Seq.empty
      case ResolveMethodCall(method) if GetSet(method.name) && !applyUpdateParameterNames.get() => Seq.empty
      case call: ScMethodCall => call.matchedParameters.reverse
      case call: ScConstructor => call.matchedParameters
      case _ => Seq.empty
    }).filter {
      case (argument, _) => element.isAncestorOf(argument)
    }
  }

  object methodInfo {

    import HintInfo.MethodInfo

    def unapply(element: PsiElement): Option[MethodInfo] = element match {
      case ResolveMethodCall(methodInfo(info)) => Some(info)
      case ResolveConstructorCall(methodInfo(info)) => Some(info)
      case _ => None
    }

    private def unapply(method: PsiMethod) = {
      val classFqn = method.containingClass match {
        case null => ""
        case clazz => s"${clazz.qualifiedName}."
      }

      val names = method.parameters.map(_.name)

      import JavaConverters._
      Some(new MethodInfo(classFqn + method.name, names.asJava))
    }
  }

  private object ResolveMethodCall {

    def unapply(call: ScMethodCall): Option[PsiMethod] =
      call.applyOrUpdateElement.collect {
        case ScalaResolveResult(method: PsiMethod, _) => method
      }.orElse {
        call.deepestInvokedExpr match {
          case ResolvesTo(method: PsiMethod) => Some(method)
          case _ => None
        }
      }
  }

  private object ResolveConstructorCall {

    def unapply(call: ScConstructor): Option[PsiMethod] =
      call.reference.collect {
        case ResolvesTo(method: PsiMethod) => method
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
