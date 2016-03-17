package org.jetbrains.plugins.scala
package codeInsight.intention.booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.booleans.SimplifyBooleanUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.annotation.tailrec

/**
 * Nikolay.Tropin
 * 4/29/13
 */
object SimplifyBooleanExprWithLiteralIntention {
  def familyName = "Simplify boolean expression with a literal"
}

class SimplifyBooleanExprWithLiteralIntention extends PsiElementBaseIntentionAction{
  def getFamilyName = SimplifyBooleanExprWithLiteralIntention.familyName

  override def getText = "Simplify boolean expression"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    findSimplifiableParent(element)(project.typeSystem).exists {
      case expr =>
        val offset = editor.getCaretModel.getOffset
        offset >= expr.getTextRange.getStartOffset && offset <= expr.getTextRange.getEndOffset
    }
  }

  def invoke(project: Project, editor: Editor, element: PsiElement) {
    implicit val typeSystem = project.typeSystem
     findSimplifiableParent(element) match {
       case Some(expr) =>
         inWriteAction {
           expr.replaceExpression(SimplifyBooleanUtil.simplify(expr), removeParenthesis = true)
         }
       case _ =>
    }
  }

  @tailrec
  private def findSimplifiableParent(element: PsiElement)
                                    (implicit typeSystem: TypeSystem): Option[ScExpression] = element.getParent match {
    case expr: ScExpression =>
      if (SimplifyBooleanUtil.canBeSimplified(expr)) Some(expr)
      else findSimplifiableParent(expr)
    case _ => None
  }
}