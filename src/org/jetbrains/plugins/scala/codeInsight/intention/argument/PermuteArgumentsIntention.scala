package org.jetbrains.plugins.scala
package codeInsight.intention.argument

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

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
    if (argList == null) return None

    val argsAndMatchingParams: Seq[(ScExpression, Parameter)] = argList.matchedParameters.sortBy(_._1.getTextOffset)
    val argumentParamIndices: Seq[Int] = argsAndMatchingParams.map(_._2.index)
    val sorted: Seq[Int] = argumentParamIndices.sorted
    if (argumentParamIndices != sorted) {
      val doIt = () => {
        val argsCopy = argList.exprs.map(_.copy)
        argList.exprs.zipWithIndex.foreach {
          case (argExpr, i) =>
            val i2 = argumentParamIndices.indexOf(sorted(i))
            argExpr.replace(argsCopy(i2))
        }
      }
      Some(doIt)
    } else None
  }
}
