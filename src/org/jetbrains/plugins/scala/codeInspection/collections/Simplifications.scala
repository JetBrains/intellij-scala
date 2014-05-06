package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.lang.psi.api.expr._
import Utils._
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScFunctionType, ScType, ScParameterizedType, ScDesignatorType}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiManager, ScalaPsiElementFactory}
import com.intellij.psi.search.GlobalSearchScope
import scala.Some

/**
 * Nikolay.Tropin
 * 5/21/13
 */
class Simplification(val replacementText: String, val hint: String, val rangeInParent: TextRange)

abstract class SimplificationType(inspection: OperationOnCollectionInspection) {
  def hint: String
  def description: String = hint
  def getSimplification(single: MethodRepr): List[Simplification] = Nil
  def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = Nil

  val likeOptionClasses = inspection.getLikeOptionClasses
  val likeCollectionClasses = inspection.getLikeCollectionClasses

  def createSimplification(methodToBuildFrom: MethodRepr,
                           parentExpr: ScExpression,
                           args: Seq[ScExpression],
                           newMethodName: String): List[Simplification] = {
    val rangeInParent = methodToBuildFrom.rightRangeInParent(parentExpr)
    methodToBuildFrom.itself match {
      case ScInfixExpr(left, _, right) if args.size == 1 =>
        List(new Simplification(s"${left.getText} $newMethodName ${args(0).getText}", hint, rangeInParent))
      case _: ScMethodCall | _: ScInfixExpr =>
        methodToBuildFrom.optionalBase match {
          case Some(baseExpr) =>
            val baseText = baseExpr.getText
            val argsText = bracedArgs(args)
            List(new Simplification(s"$baseText.$newMethodName$argsText", hint, rangeInParent))
          case _ => Nil
        }
      case _ => Nil
    }
  }

  def swapMethodsSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    val range = second.rightRangeInParent(last.itself)
    def refWithArgumentsText(method: MethodRepr): Option[String] = (method.itself, method.optionalBase) match {
      case (_: ScMethodCall | _: ScReferenceExpression, Some(baseExpr)) =>
        val startIndex = baseExpr.getTextRange.getEndOffset - method.itself.getTextRange.getStartOffset
        val text = method.itself.getText
        if (startIndex > 0 && startIndex < text.length) Option(text.substring(startIndex))
        else None
      case (ScInfixExpr(left, op, right), _) => Some(s".${op.refName}${bracedArg(method, right)}")
      case _ => None
    }
    for {
      lastText <- refWithArgumentsText(last)
      secondText <- refWithArgumentsText(second)
      baseExpr <- second.optionalBase
    } {
      return List(new Simplification(s"${baseExpr.getText}$lastText$secondText", hint, range))
    }
    Nil
  }

  private def bracedArg(method: MethodRepr, argText: String) = method.args match {
    case Seq(_: ScBlock) => argText
    case Seq(_: ScParenthesisedExpr) => argText
    case _ if argText == "" => ""
    case _ => s"($argText)"
  }

  private def bracedArgs(args: Seq[ScExpression]) = {
    args.map {
      case p: ScParenthesisedExpr if p.expr.isDefined => p.getText
      case ScBlock(stmt: ScBlockStatement) => s"(${stmt.getText})"
      case b: ScBlock => b.getText
      case other => s"(${other.getText})"
    }.mkString
  }
}