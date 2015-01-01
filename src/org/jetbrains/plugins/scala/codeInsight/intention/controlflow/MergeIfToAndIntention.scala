package org.jetbrains.plugins.scala.codeInsight.intention.controlflow

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

object MergeIfToAndIntention {
  def familyName = "Merge nested Ifs to ANDed condition"
}

class MergeIfToAndIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = MergeIfToAndIntention.familyName

  override def getText: String = "Merge nested 'if's"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val ifStmt: ScIfStmt = PsiTreeUtil.getParentOfType(element, classOf[ScIfStmt], false)
    if (ifStmt == null) return false

    val offset = editor.getCaretModel.getOffset
    val thenBranch =  ifStmt.thenBranch.orNull
    val elseBranch =  ifStmt.elseBranch.orNull
    if (thenBranch == null || elseBranch != null) return false

    val condition = ifStmt.condition.orNull
    if (condition == null) return false

    if (!(ifStmt.getTextRange.getStartOffset <= offset &&
      offset <= condition.getTextRange.getStartOffset - 1)) return false

    thenBranch match {
      case branch: ScBlockExpr =>
        val exprs = branch.exprs
        if (exprs.size != 1 || !exprs(0).isInstanceOf[ScIfStmt]) return false

        val innerIfStmt = exprs(0).asInstanceOf[ScIfStmt]
        val innerElseBranch = innerIfStmt.elseBranch.orNull
        if (innerElseBranch != null) return false
        true

      case branch: ScIfStmt =>
        val innerElseBranch = branch.elseBranch.orNull
        if (innerElseBranch != null) return false
        true

      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val ifStmt : ScIfStmt = PsiTreeUtil.getParentOfType(element, classOf[ScIfStmt], false)
    if (ifStmt == null || !ifStmt.isValid) return

    val expr = new StringBuilder
    val outerCondition = ifStmt.condition.get.getText
    val innerIfStmt = ifStmt.thenBranch.get match {
      case c: ScBlockExpr => c.exprs(0).asInstanceOf[ScIfStmt]
      case c: ScIfStmt => c
    }
    val innerThenBranch = innerIfStmt.thenBranch.get
    val innerCondition = innerIfStmt.condition.get.getText

    expr.append("if (").append(outerCondition).append(" && ").append(innerCondition).append(") ").
      append(innerThenBranch.getText)

    val newIfStmt: ScExpression = ScalaPsiElementFactory.createExpressionFromText(expr.toString(), element.getManager)

    inWriteAction {
      ifStmt.replaceExpression(newIfStmt, true)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }
}

