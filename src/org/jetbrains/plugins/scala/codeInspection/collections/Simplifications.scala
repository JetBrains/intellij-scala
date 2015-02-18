package org.jetbrains.plugins.scala
package codeInspection.collections

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import extensions.childOf

/**
 * Nikolay.Tropin
 * 5/21/13
 */
case class Simplification(replacementText: String, hint: String, rangeInParent: TextRange)

abstract class SimplificationType(inspection: OperationOnCollectionInspection) {
  def hint: String
  def description: String = hint
  def getSimplification(single: MethodRepr): List[Simplification] = Nil
  def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = Nil

  def likeOptionClasses = inspection.getLikeOptionClasses
  def likeCollectionClasses = inspection.getLikeCollectionClasses

  def isCollectionMethod(expr: ScExpression) = OperationOnCollectionsUtil.checkResolve(expr, likeCollectionClasses)
  def isOptionMethod(expr: ScExpression) = OperationOnCollectionsUtil.checkResolve(expr, likeOptionClasses)

  def createSimplification(methodToBuildFrom: MethodRepr,
                           parentExpr: ScExpression,
                           newMethodName: String,
                           args: Seq[ScExpression]*): List[Simplification] = {
    val rangeInParent = methodToBuildFrom.rightRangeInParent(parentExpr)
    methodToBuildFrom.itself match {
      case ScInfixExpr(left, _, right) if args.flatten.size == 1 =>
        List(new Simplification(s"${left.getText} $newMethodName ${args(0)(0).getText}", hint, rangeInParent))
      case _: ScMethodCall | _: ScInfixExpr | _: ScReferenceExpression =>
        methodToBuildFrom.optionalBase match {
          case Some(baseExpr) =>
            val needParenths = baseExpr.isInstanceOf[ScInfixExpr]
            val baseText = if (needParenths) s"(${baseExpr.getText})" else baseExpr.getText
            val argsText = bracedArgs(args: _*)
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

  private def bracedArgs(args: Seq[ScExpression]*) = {
    args.map {
      case Seq(p: ScParenthesisedExpr) => p.getText
      case Seq(ScBlock(stmt: ScBlockStatement)) => s"(${stmt.getText})"
      case Seq(b: ScBlock) => b.getText
      case Seq((fe: ScFunctionExpr) childOf (b: ScBlockExpr)) => b.getText
      case Seq(other) => s"(${other.getText})"
      case seq if seq.size > 1 => seq.map(_.getText).mkString("(", ", ", ")")
      case _ => ""
    }.mkString
  }
}