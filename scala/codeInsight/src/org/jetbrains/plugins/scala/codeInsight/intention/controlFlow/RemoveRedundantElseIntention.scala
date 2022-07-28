package org.jetbrains.plugins.scala
package codeInsight
package intention
package controlFlow

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiManager}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createNewLine

final class RemoveRedundantElseIntention extends PsiElementBaseIntentionAction {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val ifStmt: ScIf = PsiTreeUtil.getParentOfType(element, classOf[ScIf], false)
    if (ifStmt == null) return false

    val thenBranch = ifStmt.thenExpression.orNull
    val elseBranch = ifStmt.elseExpression.orNull
    val condition = ifStmt.condition.orNull
    if (thenBranch == null || elseBranch == null || condition == null) return false

    val offset = editor.getCaretModel.getOffset
    if (!(thenBranch.getTextRange.getEndOffset <= offset && offset <= elseBranch.getTextRange.getStartOffset))
      return false

    thenBranch match {
      case tb: ScBlockExpr =>
        val lastExpr = tb.resultExpression.orNull
        if (lastExpr == null) return false
        if (lastExpr.isInstanceOf[ScReturn]) return true
        if (lastExpr.isInstanceOf[ScThrow]) return true
        false
      case e: ScExpression =>
        if (e.isInstanceOf[ScReturn]) return true
        if (e.isInstanceOf[ScThrow]) return true
        false
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val ifStmt: ScIf = PsiTreeUtil.getParentOfType(element, classOf[ScIf], false)
    if (ifStmt == null || !ifStmt.isValid) return

    val thenBranch = ifStmt.thenExpression.getOrElse(return)
    val elseKeyWord = thenBranch.getNextSiblingNotWhitespaceComment

    val elseBranch = ifStmt.elseExpression.getOrElse(return)

    val children = elseBranch.copy().children.toList
    var from = children.find(_.getNode.getElementType != ScalaTokenTypes.tLBRACE).getOrElse(return)
    if (ScalaTokenTypes.WHITES_SPACES_TOKEN_SET.contains(from.getNode.getElementType)) from = from.getNextSibling
    val to = children.findLast(_.getNode.getElementType != ScalaTokenTypes.tRBRACE).getOrElse(return)

    inWriteAction {
      elseKeyWord.delete()
      elseBranch.delete()
      ifStmt.getParent.addRangeAfter(from, to, ifStmt)
      ifStmt.getParent.addAfter(createNewLine()(PsiManager.getInstance(project)), ifStmt)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }

  override def getFamilyName: String = ScalaCodeInsightBundle.message("family.name.remove.redundant.else")

  override def getText: String = ScalaCodeInsightBundle.message("remove.redundant.else")
}

