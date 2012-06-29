package org.jetbrains.plugins.scala.codeInsight.intention.controlflow

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.extensions._
import com.intellij.codeInsight.PsiEquivalenceUtil
import java.util

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

    if (!(thenBranch.getTextRange.getEndOffset <= offset && offset <= elseBranch.getTextRange.getStartOffset) &&
    !(ifStmt.getTextRange.getStartOffset <= offset && offset <= ifStmt.condition.get.getTextRange.getStartOffset))
    return false

    val innerThenBranch = elseBranch.asInstanceOf[ScIfStmt].thenBranch.getOrElse(null)
    if (innerThenBranch == null) return false

    val comparator = new util.Comparator[PsiElement]() {
      def compare(element1: PsiElement, element2: PsiElement): Int = {
        if (element1 == element2) return 0
        if (element1.isInstanceOf[ScBlockExpr] && element2.isInstanceOf[ScBlockExpr]) {
          val size1 = element1.asInstanceOf[ScBlockExpr].exprs.size
          val size2 = element2.asInstanceOf[ScBlockExpr].exprs.size
          if (size1 != size2) return 1
          if (element1.asInstanceOf[ScBlockExpr] == element2.asInstanceOf[ScBlockExpr]) return 0
        }
        if (element1.isInstanceOf[ScExpression] && element2.isInstanceOf[ScExpression]) {
          if (element1.asInstanceOf[ScExpression] == element2.asInstanceOf[ScExpression]) return 0
        }
        1
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
