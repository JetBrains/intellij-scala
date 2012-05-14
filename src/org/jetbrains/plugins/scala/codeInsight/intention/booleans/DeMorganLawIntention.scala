package org.jetbrains.plugins.scala
package codeInsight.intention.booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.util.TextRange
import lang.psi.impl.ScalaPsiElementFactory
import extensions._
import org.codehaus.groovy.ast.expr.PrefixExpression
import lang.psi.api.base.{ScLiteral, ScReferenceElement}
import lang.psi.api.expr._
import lang.refactoring.util.ScalaNamesUtil
import lang.lexer.ScalaTokenTypes
import com.intellij.psi.{PsiManager, PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.util.IntentionUtils

/**
 * @author Ksenia.Sautina
 * @since 5/12/12
 */

object DeMorganLawIntention {
  def familyName = "DeMorgan Law"
}

class DeMorganLawIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = DeMorganLawIntention.familyName

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

    def negate(expression: ScExpression): String = {
      expression match {
        case e: ScPrefixExpr =>
          if (e.operation.getText == "!") e.getBaseExpr.getText
          else "!(" + e.getText + ")"
        case e: ScLiteral =>
          if (e.getNode.getFirstChildNode.getElementType == ScalaTokenTypes.kTRUE) "false"
          else if (e.getNode.getFirstChildNode.getElementType == ScalaTokenTypes.kFALSE) "true"
          else "!" + e.getText
        case _ =>
          val exprText = expression.getText
          if (ScalaNamesUtil.isOpCharacter(exprText(0))) "!(" + exprText + ")"
          else "!" + expression.getText
      }
    }

    val replaceOper = Map("&&" -> "||", "||" -> "&&")

    val start = infixExpr.getTextRange.getStartOffset
    val diff = editor.getCaretModel.getOffset - infixExpr.operation.nameId.getTextRange.getStartOffset

    val buf = new StringBuilder
    buf.append(negate(infixExpr.getBaseExpr)).append(" ").
            append(replaceOper(infixExpr.operation.nameId.getText)).append(" ").append(negate(infixExpr.getArgExpr))

    val res = IntentionUtils.negateAndValidateExpression(infixExpr, element.getManager, buf)

    inWriteAction {
      res._1.replaceExpression(res._2, true)
      editor.getCaretModel.moveToOffset(start + diff + res._3)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }
}