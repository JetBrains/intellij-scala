package org.jetbrains.plugins.scala.codeInsight.intention.controlflow

import java.util

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * @author Ksenia.Sautina
 * @since 6/6/12
 */

object MergeIfToOrIntention {
  def familyName = "Merge equivalent Ifs to ORed condition"
}

class MergeIfToOrIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = MergeIfToOrIntention.familyName

  override def getText: String = "Merge sequential 'if's"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val ifStmt: ScIfStmt = PsiTreeUtil.getParentOfType(element, classOf[ScIfStmt], false)
    if (ifStmt == null) return false

    val offset = editor.getCaretModel.getOffset
    val thenBranch =  ifStmt.thenBranch.getOrElse(null)
    val elseBranch =  ifStmt.elseBranch.getOrElse(null)
    if (thenBranch == null || elseBranch == null) return false

    if (!elseBranch.isInstanceOf[ScIfStmt]) return false
    if (ifStmt.condition.getOrElse(null) == null) return false

    if (!(thenBranch.getTextRange.getEndOffset <= offset && offset <= elseBranch.getTextRange.getStartOffset) &&
    !(ifStmt.getTextRange.getStartOffset <= offset && offset <= ifStmt.condition.get.getTextRange.getStartOffset))
    return false

    val innerThenBranch = elseBranch.asInstanceOf[ScIfStmt].thenBranch.getOrElse(null)
    if (innerThenBranch == null) return false

    val comparator = new util.Comparator[PsiElement]() {
      def compare(element1: PsiElement, element2: PsiElement): Int = {
        (element1, element2) match {
          case _ if element1 == element2 =>  0
          case (block1: ScBlockExpr, block2: ScBlockExpr) if block1.exprs.size != block2.exprs.size => 1
          case (block1: ScBlockExpr, block2: ScBlockExpr) if block1 == block2 => 0
          case (expr1: ScExpression, expr2: ScExpression) if expr1 == expr2 => 0
          case _ => 1
        }
      }
    }

    PsiEquivalenceUtil.areElementsEquivalent(thenBranch, innerThenBranch, comparator, false)
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val ifStmt : ScIfStmt = PsiTreeUtil.getParentOfType(element, classOf[ScIfStmt], false)
    if (ifStmt == null || !ifStmt.isValid) return

    val start = ifStmt.getTextRange.getStartOffset
    val expr = new StringBuilder
    val outerCondition = ifStmt.condition.get.getText
    val innerIfStmt = ifStmt.elseBranch.get.asInstanceOf[ScIfStmt]
    val innerCondition = innerIfStmt.condition.get.getText
    val innerElseBranch = innerIfStmt.elseBranch.getOrElse(null)

    expr.append("if (").append(outerCondition).append(" || ").append(innerCondition).append(") ").
      append(ifStmt.thenBranch.get.getText)
    if (innerElseBranch != null) expr.append(" else ").append(innerElseBranch.getText)

    val newIfStmt : ScExpression = ScalaPsiElementFactory.createExpressionFromText(expr.toString(), element.getManager)

    inWriteAction {
      ifStmt.replaceExpression(newIfStmt, true)
      editor.getCaretModel.moveToOffset(start)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }
}
