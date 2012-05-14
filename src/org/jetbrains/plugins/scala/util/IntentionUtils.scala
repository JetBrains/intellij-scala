package org.jetbrains.plugins.scala
package util

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.util.PsiTreeUtil
import collection.Seq
import lang.psi.types.nonvalue.Parameter
import lang.psi.api.base.ScLiteral
import lang.psi.api.expr.xml.ScXmlExpr
import lang.psi.api.expr._

/**
 * @author Ksenia.Sautina
 * @since 5/12/12
 */

object IntentionUtils {

  def check(project: Project, element: PsiElement): Option[() => Unit] = {
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
            argsAndMatchingParams.headOption match {
              case _ if isRepeated => None
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

}
