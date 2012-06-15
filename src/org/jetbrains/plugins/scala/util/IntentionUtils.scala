package org.jetbrains.plugins.scala
package util

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.util.PsiTreeUtil
import collection.Seq
import lang.psi.types.nonvalue.Parameter
import lang.psi.api.base.ScLiteral
import lang.psi.api.expr.xml.ScXmlExpr
import lang.psi.api.expr._
import com.intellij.psi.{PsiManager, PsiElement}
import lang.lexer.ScalaTokenTypes
import lang.refactoring.util.ScalaNamesUtil

/**
 * @author Ksenia.Sautina
 * @since 5/12/12
 */

object IntentionUtils {

  def check(element: PsiElement): Option[() => Unit] = {
    val containingArgList: Option[ScArgumentExprList] = element.parents.collectFirst {
      case al: ScArgumentExprList if !al.isBraceArgs => al
    }
    containingArgList match {
      case Some(al) =>
        val index = al.exprs.indexWhere(argExpr => PsiTreeUtil.isAncestor(argExpr, element, false))
        index match {
          case -1 => None
          case i =>
            val argExprsToNamify = al.exprs.drop(index)
            val argsAndMatchingParams: Seq[(ScExpression, Option[Parameter])] = argExprsToNamify.map {
              arg => (arg, al.parameterOf(arg))
            }
            val isRepeated = argsAndMatchingParams.exists {
              case (_, Some(param)) if param.isRepeated => true
              case _ => false
            }
            val hasName = argsAndMatchingParams.exists {
              case (_, Some(param)) if (!param.name.isEmpty) => true
              case _ => false
            }
            val hasUnderscore = argsAndMatchingParams.exists {
              case (_, Some(param)) if (param.isInstanceOf[ScUnderscoreSection]) => true
              case (underscore: ScUnderscoreSection, Some(param)) => true
              case param: ScUnderscoreSection => true
              case _ => false
            }
            argsAndMatchingParams.headOption match {
              case _ if isRepeated => None
              case _ if !hasName => None
              case _ if hasUnderscore => None
              case Some((assign: ScAssignStmt, Some(param))) if assign.getLExpression.getText == param.name =>
                None
              case None | Some((_, None)) =>
                None
              case _ =>
                val doIt = () => {
                  argsAndMatchingParams.foreach {
                    case (argExpr: ScAssignStmt, Some(param)) if argExpr.getLExpression.getText == param.name =>
                    case (argExpr, Some(param)) =>
                      val newArgExpr = ScalaPsiElementFactory.createExpressionFromText(param.name + " = " + argExpr.getText, element.getManager)
                      inWriteAction {
                        argExpr.replace(newArgExpr)
                      }
                    case _ =>
                  }
                }
                Some(doIt)
            }
        }
      case None => None
    }
  }

  def analyzeMethodCallArgs(methodCallArgs: ScArgumentExprList, argsBuilder: scala.StringBuilder) {
    if (methodCallArgs.exprs.length == 1) {
      methodCallArgs.exprs.head match {
        case _: ScLiteral => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case _: ScTuple => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case _: ScReferenceExpression => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case _: ScGenericCall => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case _: ScXmlExpr => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case _: ScMethodCall => argsBuilder.replace(argsBuilder.length - 1, argsBuilder.length, "").replace(0, 1, "")
        case infix: ScInfixExpr if (infix.getBaseExpr.isInstanceOf[ScUnderscoreSection]) =>
          argsBuilder.insert(0, "(").append(")")
        case _ => argsBuilder
      }
    }
  }

  def negateAndValidateExpression(infixExpr: ScInfixExpr, manager: PsiManager, buf: scala.StringBuilder) = {
    val parent =
      if (infixExpr.getParent != null && infixExpr.getParent.isInstanceOf[ScParenthesisedExpr]) infixExpr.getParent.getParent
      else infixExpr.getParent

    if (parent != null && parent.isInstanceOf[ScPrefixExpr] &&
            parent.asInstanceOf[ScPrefixExpr].operation.getText == "!") {

      val newExpr = ScalaPsiElementFactory.createExpressionFromText(buf.toString(), manager)

      val size = newExpr.asInstanceOf[ScInfixExpr].operation.nameId.getTextRange.getStartOffset -
              newExpr.getTextRange.getStartOffset - 2

      (parent.asInstanceOf[ScPrefixExpr], newExpr, size)
    } else {
      buf.insert(0, "!(").append(")")
      val newExpr = ScalaPsiElementFactory.createExpressionFromText(buf.toString(), manager)

      val children = newExpr.asInstanceOf[ScPrefixExpr].getLastChild.asInstanceOf[ScParenthesisedExpr].getChildren
      val size = children(0).asInstanceOf[ScInfixExpr].operation.
              nameId.getTextRange.getStartOffset - newExpr.getTextRange.getStartOffset

      (infixExpr, newExpr, size)
    }
  }

  def negate(expression: ScExpression): String = {
    expression match {
      case e: ScPrefixExpr =>
        if (e.operation.getText == "!") {
          val exprWithoutParentheses =
            if (e.getBaseExpr.isInstanceOf[ScParenthesisedExpr]) e.getBaseExpr.getText.drop(1).dropRight(1)
            else e.getBaseExpr.getText
          val newExpr = ScalaPsiElementFactory.createExpressionFromText(exprWithoutParentheses, expression.getManager)
          inWriteAction {
            e.replaceExpression(newExpr, true).getText
          }
        }
        else "!(" + e.getText + ")"
      case e: ScLiteral =>
        if (e.getNode.getFirstChildNode.getElementType == ScalaTokenTypes.kTRUE) "false"
        else if (e.getNode.getFirstChildNode.getElementType == ScalaTokenTypes.kFALSE) "true"
        else "!" + e.getText
      case _ =>
        val exprText = expression.getText
        if (ScalaNamesUtil.isOpCharacter(exprText(0))) "!(" + exprText + ")"
        else "!" + expression.getText
    }
  }
}
