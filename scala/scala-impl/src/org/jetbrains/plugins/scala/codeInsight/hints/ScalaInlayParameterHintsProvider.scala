package org.jetbrains.plugins.scala
package codeInsight
package hints

import java.{util => ju}

import com.intellij.codeInsight.hints
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.Ext
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec
import scala.collection.JavaConverters

class ScalaInlayParameterHintsProvider extends hints.InlayParameterHintsProvider {

  import Ext.GetSet
  import ScalaInlayParameterHintsProvider._

  override def getParameterHints(element: PsiElement): ju.List[hints.InlayInfo] = {
    val matchedParameters = (element match {
      case ResolveMethodCall(method) if GetSet(method.name) && !applyUpdateParameterNames.isEnabled => Seq.empty
      case call: ScMethodCall => call.matchedParameters.reverse
      case call: ScConstructor => call.matchedParameters
      case _ => Seq.empty
    }).filter {
      case (argument, _) => element.isAncestorOf(argument)
    }

    matchedParameters match {
      case Seq() => ju.Collections.emptyList()
      case _ => parameterHints(matchedParameters)
    }
  }

  override final def getHintInfo(element: PsiElement): hints.HintInfo = element match {
    case ResolveMethodCall(methodInfo(info)) => info
    case ResolveConstructorCall(methodInfo(info)) => info
    case _ => null
  }

  override final def getInlayPresentation(inlayText: String): String = inlayText

  override final def getSupportedOptions: ju.List[hints.Option] = ju.Arrays.asList(
    applyUpdateParameterNames,
    referenceParameterNames
  )

  override final def getDefaultBlackList: ju.Set[String] = ju.Collections.singleton("scala.*")

  override final def getBlackListDependencyLanguage: JavaLanguage = JavaLanguage.INSTANCE
}

object ScalaInlayParameterHintsProvider {

  import Ext.{Apply, Update}

  private[hints] val applyUpdateParameterNames = HintOption(s"<code>$Apply</code>, <code>$Update</code> methods", Apply, Update)
  private[hints] val referenceParameterNames = HintOption(s"non-literal expressions", "references", "names")

  private[this] object HintOption {

    def apply(nameSuffix: String, idSegments: String*): hints.Option = {
      val id = "scala" +: idSegments :+ "hint"
      new hints.Option(id.mkString("."), s"<html><body>Show for $nameSuffix</body></html>", false)
    }
  }

  private def parameterHints(matchedParameters: Seq[(ScExpression, Parameter)]) = {
    val (varargs, regular) = matchedParameters.partition {
      case (_, parameter) => parameter.isRepeated
    }

    val result = (regular ++ varargs.headOption).filter {
      case (argument, parameter) => isNameable(argument) && isNameable(parameter)
    }.filter {
      case (_: ScUnderscoreSection, _) => false
      case (argument, _) if !referenceParameterNames.isEnabled => isUnclear(argument)
      case (extractReferenceName(name), parameter) => name.mismatchesCamelCase(parameter.name)
      case _ => true
    }.map {
      case (argument, parameter) => InlayInfo(parameter, argument)
    }

    import JavaConverters._
    result.asJava
  }

  private object methodInfo {

    import hints.HintInfo.MethodInfo

    def unapply(method: PsiMethod): Some[MethodInfo] = {
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

  private[this] def isNameable(argument: ScExpression) =
    argument.getParent match {
      case list: ScArgumentExprList => !list.isBraceArgs
      case _ => false
    }

  private[this] def isNameable(parameter: Parameter) =
    parameter.name.length > 1

  @tailrec
  private[this] def isUnclear(expression: ScExpression): Boolean = expression match {
    case _: ScLiteral | _: ScThisReference => true
    case ScParenthesisedExpr(inner) => isUnclear(inner)
    case ScSugarCallExpr(base, _, _) => isUnclear(base)
    case _ => false
  }

  private[this] object extractReferenceName {

    def unapply(expression: ScExpression): Option[String] = expression match {
      case MethodRepr(_, maybeExpression, maybeReference, Seq()) =>
        maybeReference.orElse(maybeExpression).collect {
          case reference: ScReferenceExpression => reference.refName
        }
      case _ => None
    }
  }

  private[this] implicit class CamelCaseExt(private val string: String) extends AnyVal {

    def mismatchesCamelCase(that: String): Boolean =
      camelCaseIterator.zip(that.camelCaseIterator).exists {
        case (leftSegment, rightSegment) => leftSegment != rightSegment
      }

    def camelCaseIterator: Iterator[String] = ScalaNamesUtil
      .isBacktickedName
      .unapply(string)
      .getOrElse(string)
      .split("(?<!^)(?=[A-Z])")
      .reverseIterator
      .map(_.toLowerCase)
  }

}
