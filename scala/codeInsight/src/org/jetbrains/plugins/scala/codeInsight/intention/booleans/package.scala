package org.jetbrains.plugins.scala
package codeInsight
package intention

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.project.ProjectContext

package object booleans {

  def negateAndValidateExpression(infix: ScInfixExpr, text: String)
                                 (implicit project: Project, editor: Editor): Unit = {
    val caretModel = editor.getCaretModel
    val caretAnchor = Anchor.find(infix, caretModel.getOffset)

    val (anchor, replacement) = negateAndValidateExpressionImpl(infix, text)

    IntentionPreviewUtils.write { () =>
      val replaced = anchor.replaceExpression(replacement, removeParenthesis = true)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
      caretAnchor
        .flatMap(_.caretPosition(replaced))
        .foreach(caretModel.moveToOffset)
    }
  }

  private def negateAndValidateExpressionImpl(infix: ScInfixExpr, text: String)
                                             (implicit context: ProjectContext): (ScExpression, ScExpression) = {
    val parent = infix.getParent match {
      case p: ScParenthesisedExpr => p.getParent
      case p => p
    }

    parent match {
      case prefix: ScPrefixExpr if prefix.operation.textMatches("!") =>
        val newExpr = createExpressionFromText(text, infix)
        (parent.asInstanceOf[ScPrefixExpr], newExpr)
      case _ =>
        val newExpr = createExpressionFromText("!(" + text + ")", infix)
        (infix, newExpr)
    }
  }

  private case class Anchor(infixIdx: Int, offset: Int) {
    def caretPosition(expr: ScExpression): Option[Int] = {
      Anchor.indexedInfixExprs(expr)
        .find { case (_, idx) => idx == infixIdx }
        .map { case (e, _) => e.operation.getTextOffset + offset }
    }
  }

  private object Anchor {
    def find(expr: ScExpression, caret: Int): Option[Anchor] =
      indexedInfixExprs(expr)
        .map { case (e, idx) => Anchor(idx, caret - e.operation.getTextOffset) }
        .minByOption(_.offset.abs)

    private def indexedInfixExprs(expr: ScExpression): Seq[(ScInfixExpr, Int)] =
      expr.depthFirst()
        .filterByType[ScInfixExpr]
        .toSeq
        .sortBy(_.operation.getTextOffset)
        .zipWithIndex
  }
}
