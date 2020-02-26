package org.jetbrains.plugins.scala
package codeInsight
package intention
package argument

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression}

import scala.annotation.tailrec

/**
  * Jason Zaugg
  */
final class PermuteArgumentsIntention extends PsiElementBaseIntentionAction {

  import PermuteArgumentsIntention._

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    check(project, editor, element).isDefined

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    if (!element.isValid) return
    check(project, editor, element).foreach(_.apply())
  }

  override def getFamilyName: String = ScalaCodeInsightBundle.message("family.name.permute.arguments")

  override def getText: String = ScalaCodeInsightBundle.message("permute.arguments.to.match.the.parameter.declaration.order")
}

object PermuteArgumentsIntention {

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

  @tailrec
  private[this] def argOrNamedArg(expr: ScExpression): Option[ScExpression] = expr.getContext match {
    case argList: ScArgumentExprList => Some(expr)
    case context: ScExpression => argOrNamedArg(context)
    case _ => None
  }
}
