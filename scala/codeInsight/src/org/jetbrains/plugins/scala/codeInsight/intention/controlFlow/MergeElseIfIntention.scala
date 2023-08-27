package org.jetbrains.plugins.scala
package codeInsight
package intention
package controlFlow

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createElementFromText
import org.jetbrains.plugins.scala.project.ScalaFeatures

import scala.collection.mutable

final class MergeElseIfIntention extends PsiElementBaseIntentionAction {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val ifStmt: ScIf = PsiTreeUtil.getParentOfType(element, classOf[ScIf], false)
    if (ifStmt == null) return false

    val offset = editor.getCaretModel.getOffset
    val thenBranch = ifStmt.thenExpression.orNull
    val elseBranch = ifStmt.elseExpression.orNull
    if (thenBranch == null || elseBranch == null) return false

    if (!(thenBranch.getTextRange.getEndOffset <= offset && offset <= elseBranch.getTextRange.getStartOffset))
      return false

    val blockExpr = ifStmt.elseExpression.orNull
    if (blockExpr != null && blockExpr.is[ScBlockExpr]) {
      val exprs = blockExpr.asInstanceOf[ScBlockExpr].exprs
      if (exprs.size == 1 && exprs.head.is[ScIf]) {
        return true
      }
    }

    false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val ifStmt: ScIf = PsiTreeUtil.getParentOfType(element, classOf[ScIf], false)
    if (ifStmt == null || !ifStmt.isValid) return

    val elseOffset = ifStmt.elseKeyword.get.getTextOffset
    val caretOffset = editor.getCaretModel.getOffset

    val newlineBeforeElse = ifStmt.elseKeyword.exists(_.startsFromNewLine(ignoreComments = false))
    val expr = new mutable.StringBuilder()
      .append("if (").append(ifStmt.condition.get.getText).append(") ")
      .append(ifStmt.thenExpression.get.getText)
      .append(if (newlineBeforeElse) "\n" else " ")
      .append("else ")
      .append(ifStmt.elseExpression.get.getText.trim.drop(1).dropRight(1))

    implicit val ctx: Project = element.getProject
    implicit val features: ScalaFeatures = element
    val newIfStmt = ScalaPsiUtil.convertIfToBracelessIfNeeded(createElementFromText[ScIf](expr.toString(), element), recursive = true)
    val newElseOffset = ifStmt.getTextOffset + newIfStmt.elseKeyword.get.getStartOffsetInParent

    IntentionPreviewUtils.write { () =>
      ifStmt.replaceExpression(newIfStmt, removeParenthesis = true)
      editor.getCaretModel.moveToOffset(newElseOffset - elseOffset + caretOffset)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }

  override def getFamilyName: String = ScalaCodeInsightBundle.message("family.name.merge.else.if")

  override def getText: String = ScalaCodeInsightBundle.message("merge.elseif")
}
