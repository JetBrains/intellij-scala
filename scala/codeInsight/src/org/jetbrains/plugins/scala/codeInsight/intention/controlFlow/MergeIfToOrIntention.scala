package org.jetbrains.plugins.scala
package codeInsight
package intention
package controlFlow

import java.util

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

final class MergeIfToOrIntention extends PsiElementBaseIntentionAction {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val ifStmt: ScIf = PsiTreeUtil.getParentOfType(element, classOf[ScIf], false)
    if (ifStmt == null) return false

    val offset = editor.getCaretModel.getOffset
    val thenBranch =  ifStmt.thenExpression.orNull
    val elseBranch =  ifStmt.elseExpression.orNull
    if (thenBranch == null || elseBranch == null) return false

    if (!elseBranch.isInstanceOf[ScIf]) return false
    if (ifStmt.condition.orNull == null) return false

    if (!(thenBranch.getTextRange.getEndOffset <= offset && offset <= elseBranch.getTextRange.getStartOffset) &&
    !(ifStmt.getTextRange.getStartOffset <= offset && offset <= ifStmt.condition.get.getTextRange.getStartOffset))
    return false

    val innerThenBranch = elseBranch.asInstanceOf[ScIf].thenExpression.orNull
    if (innerThenBranch == null) return false

    val comparator = new util.Comparator[PsiElement]() {
      override def compare(element1: PsiElement, element2: PsiElement): Int = {
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

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val ifStmt : ScIf = PsiTreeUtil.getParentOfType(element, classOf[ScIf], false)
    if (ifStmt == null || !ifStmt.isValid) return

    val start = ifStmt.getTextRange.getStartOffset
    val expr = new StringBuilder
    val outerCondition = ifStmt.condition.get.getText
    val innerIfStmt = ifStmt.elseExpression.get.asInstanceOf[ScIf]
    val innerCondition = innerIfStmt.condition.get.getText
    val innerElseBranch = innerIfStmt.elseExpression.orNull
    val newlineBeforeElse = ifStmt.children.find(_.getNode.getElementType == ScalaTokenTypes.kELSE).
      exists(_.getPrevSibling.getText.contains("\n"))

    expr.append("if (").append(outerCondition).append(" || ").append(innerCondition).append(") ").
      append(ifStmt.thenExpression.get.getText)
    if (innerElseBranch != null) expr.append(if (newlineBeforeElse) "\n" else " ").append("else ").
      append(innerElseBranch.getText)

    val newIfStmt: ScExpression = createExpressionFromText(expr.toString())(element.getManager)

    inWriteAction {
      ifStmt.replaceExpression(newIfStmt, removeParenthesis = true)
      editor.getCaretModel.moveToOffset(start)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }

  override def getFamilyName: String = ScalaCodeInsightBundle.message("family.name.merge.equivalent.ifs.to.ored.condition")

  override def getText: String = ScalaCodeInsightBundle.message("merge.sequential.ifs")
}
