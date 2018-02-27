package org.jetbrains.plugins.scala
package codeInsight
package hints
package hintTypes

import com.intellij.codeInsight.hints.{Option => HintOption, _}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiElementExt, PsiMethodExt, PsiNamedElementExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

import scala.collection.JavaConverters

private[hints] case object ParameterHintType
  extends HintType(defaultValue = true, idSegments = "parameter", "name") {

  def methodInfo(element: PsiElement): HintInfo.MethodInfo = {
    val extractedMethod = element match {
      case NonSyntheticMethodCall(method) => method
      case ConstructorCall(method) => method
    }

    val methodFqn = extractedMethod.getContainingClass match {
      case null => ""
      case clazz => s"${clazz.qualifiedName}.${extractedMethod.name}"
    }

    val names = extractedMethod.parameters.map(_.name)

    import JavaConverters._
    new HintInfo.MethodInfo(methodFqn, names.asJava)
  }

  override protected val delegate: HintFunction = {
    case call@NonSyntheticMethodCall(_) => withParameters(call, call.matchedParameters.reverse)
    case call@ConstructorCall(_) => withParameters(call, call.matchedParameters)
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
