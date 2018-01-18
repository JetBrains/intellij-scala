package org.jetbrains.plugins.scala
package codeInsight.intention.argument

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression}

/**
 * Jason Zaugg
 */

class PermuteArgumentsIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Permute arguments"

  override def getText = "Permute arguments to match the parameter declaration order"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
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
    val argList = PsiTreeUtil.getParentOfType(element, classOf[ScArgumentExprList])
    if (argList == null)
      return None

    val argExprs = argList.exprs

    val newArgsExprs = argList.matchedParameters.sortBy {
      case (expr, p) => (p.index, expr.getTextRange.getStartOffset)
    }.flatMap {
      case (expr, p) => argOrNamedArg(expr)
    }

    if (newArgsExprs.size != argExprs.size || newArgsExprs == argExprs)
      return None

    Some(() => argExprs.zip(newArgsExprs).foreach {
      case (arg, newArg) => arg.replace(newArg.copy)
    })
  }

  private def argOrNamedArg(expr: ScExpression): Option[ScExpression] = expr.getContext match {
    case argList: ScArgumentExprList => Some(expr)
    case context: ScExpression => argOrNamedArg(context)
    case _ => None
  }
}
