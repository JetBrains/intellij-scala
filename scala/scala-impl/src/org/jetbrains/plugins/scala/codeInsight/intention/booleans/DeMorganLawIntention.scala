package org.jetbrains.plugins.scala
package codeInsight.intention.booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.util.IntentionUtils

/**
 * @author Ksenia.Sautina
 * @since 5/12/12
 */

object DeMorganLawIntention {
  def familyName = "DeMorgan Law"
}

class DeMorganLawIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = DeMorganLawIntention.familyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val infixExpr: ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null) return false

    val oper = infixExpr.operation.nameId.getText

    if (oper != "||" && oper != "&&")
      return false

    val range: TextRange = infixExpr.operation.nameId.getTextRange
    val offset = editor.getCaretModel.getOffset
    if (!(range.getStartOffset <= offset && offset <= range.getEndOffset)) return false

    val replaceOper = Map("&&" -> "||", "||" -> "&&")
    setText("Replace '" + oper + "' with " + replaceOper(oper) + "'")

    true
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val infixExpr: ScInfixExpr = PsiTreeUtil.getParentOfType(element, classOf[ScInfixExpr], false)
    if (infixExpr == null || !infixExpr.isValid) return

    val replaceOper = Map("&&" -> "||", "||" -> "&&")

    val start = infixExpr.getTextRange.getStartOffset
    val diff = editor.getCaretModel.getOffset - infixExpr.operation.nameId.getTextRange.getStartOffset

    val buf = new StringBuilder
    buf.append(IntentionUtils.negate(infixExpr.getBaseExpr)).append(" ").
            append(replaceOper(infixExpr.operation.nameId.getText)).append(" ").
            append(IntentionUtils.negate(infixExpr.getArgExpr))

    val res = IntentionUtils.negateAndValidateExpression(infixExpr, buf)

    inWriteAction {
      res._1.replaceExpression(res._2, true)
      editor.getCaretModel.moveToOffset(start + diff + res._3)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }
}