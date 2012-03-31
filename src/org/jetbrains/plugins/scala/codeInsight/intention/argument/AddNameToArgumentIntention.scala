package org.jetbrains.plugins.scala
package codeInsight.intention.argument

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.util.PsiTreeUtil
import collection.{Seq, Iterator}
import lang.psi.api.expr.{ScExpression, ScAssignStmt, ScArgumentExprList}
import lang.psi.types.nonvalue.Parameter

/**
 * Jason Zaugg
 */

class AddNameToArgumentIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Argument Conversion"

  override def getText = "Use named arguments for current and subsequent arguments"

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    check(project, editor, element).isDefined
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    if (!element.isValid) return
    check(project, editor, element) match {
      case Some(x) => x()
      case None =>
    }
  }

  private def check(project: Project, editor: Editor, element: PsiElement): Option[() => Unit] = {
    val containingArgList: Option[ScArgumentExprList] = element.parents.collectFirst {
      case al: ScArgumentExprList => al
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
            argsAndMatchingParams.headOption match {
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
