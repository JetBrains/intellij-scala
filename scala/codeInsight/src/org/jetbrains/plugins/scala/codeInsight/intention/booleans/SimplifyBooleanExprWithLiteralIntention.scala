package org.jetbrains.plugins.scala
package codeInsight.intention.booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.booleans.SimplifyBooleanUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._

import scala.annotation.tailrec

/**
 * Nikolay.Tropin
 * 4/29/13
 */
final class SimplifyBooleanExprWithLiteralIntention extends PsiElementBaseIntentionAction {

  import SimplifyBooleanExprWithLiteralIntention._

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    findSimplifiableParent(element).exists { expr =>
        val offset = editor.getCaretModel.getOffset
        offset >= expr.getTextRange.getStartOffset && offset <= expr.getTextRange.getEndOffset
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
     findSimplifiableParent(element) match {
       case Some(expr) =>
         inWriteAction {
           expr.replaceExpression(SimplifyBooleanUtil.simplify(expr), removeParenthesis = true)
         }
       case _ =>
    }
  }

  override def getFamilyName: String = FamilyName

  override def getText = "Simplify boolean expression"
}

object SimplifyBooleanExprWithLiteralIntention {

  private[booleans] val FamilyName = "Simplify boolean expression with a literal"

  @tailrec
  private def findSimplifiableParent(element: PsiElement): Option[ScExpression] = element.getParent match {
    case expr: ScExpression =>
      if (SimplifyBooleanUtil.canBeSimplified(expr)) Some(expr)
      else findSimplifiableParent(expr)
    case _ => None
  }
}