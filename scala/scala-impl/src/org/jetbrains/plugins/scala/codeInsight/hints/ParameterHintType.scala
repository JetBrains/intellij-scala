package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.codeInsight.hints.{Option => HintOption, _}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

import scala.collection.JavaConverters

private[hints] case object ParameterHintType extends HintType {

  override val options: Seq[HintOption] = Seq(
    HintOption(defaultValue = true, "parameter", "name")
  )

  override def apply(element: PsiElement): Seq[InlayInfo] = element match {
    case _ if !options.head.get => Seq.empty
    case call@NonSyntheticMethodCall(_) => withParameters(call, call.matchedParameters.reverse)
    case call@ConstructorCall(_) => withParameters(call, call.matchedParameters)
    case _ => Seq.empty
  }

  object methodInfo {

    import HintInfo.MethodInfo

    def unapply(element: PsiElement): Option[MethodInfo] = {
      val maybeMethod = element match {
        case NonSyntheticMethodCall(method) => Some(method)
        case ConstructorCall(method) => Some(method)
        case _ => None
      }

      maybeMethod.map { method =>
        val methodFqn = method.getContainingClass match {
          case null => ""
          case clazz => s"${clazz.qualifiedName}.${method.name}"
        }

        val names = method.parameters.map(_.name)

        import JavaConverters._
        new MethodInfo(methodFqn, names.asJava)
      }
    }
  }

  private object NonSyntheticMethodCall {

    def unapply(methodCall: ScMethodCall): Option[PsiMethod] = methodCall.deepestInvokedExpr match {
      case ScReferenceExpression(method: PsiMethod) if !method.isParameterless && methodCall.argumentExpressions.nonEmpty => Some(method)
      case _ => None
    }
  }

  private object ConstructorCall {

    def unapply(constructor: ScConstructor): Option[PsiMethod] =
      if (PsiTreeUtil.getContextOfType(constructor, classOf[ScNewTemplateDefinition]) != null &&
        constructor.arguments.nonEmpty) {
        constructor.reference.collect {
          case ResolvesTo(method: PsiMethod) => method
        }
      } else None
  }

  private def withParameters(call: PsiElement,
                             matchedParameters: Seq[(ScExpression, Parameter)]): Seq[InlayInfo] = {
    val (varargs, regular) = matchedParameters.filter {
      case (argument, _) => call.isAncestorOf(argument)
    }.partition {
      case (_, parameter) => parameter.isRepeated
    }

    (regular ++ varargs.headOption).filter {
      case (MethodCall(name), parameter) if name == parameter.name => false
      case (argument, parameter) => isNameable(argument) && isNameable(parameter)
    }.map {
      case (argument, parameter) => InlayInfo(parameter, argument)
    }
  }

  private object MethodCall {

    def unapply(expression: ScExpression): Option[String] = expression match {
      case MethodRepr(_, maybeExpression, maybeReference, Seq()) =>
        maybeReference.orElse(maybeExpression).collect {
          case reference: ScReferenceExpression => reference.refName
        }
      case _ => None
    }
  }

  private def isNameable(argument: ScExpression) =
    argument.parent.collect {
      case list: ScArgumentExprList => list
    }.exists(!_.isBraceArgs)

  private def isNameable(parameter: Parameter) =
    parameter.name.length > 1
}
