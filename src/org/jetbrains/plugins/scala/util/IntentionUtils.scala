package org.jetbrains.plugins.scala
package util

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.util.PsiTreeUtil
import collection.Seq
import lang.psi.api.expr.{ScExpression, ScAssignStmt, ScArgumentExprList}
import lang.psi.types.nonvalue.Parameter

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
                      argExpr.replace(newArgExpr)
                    case _ =>
                  }
                }
                Some(doIt)
            }
        }
      case None => None
    }
  }

}
