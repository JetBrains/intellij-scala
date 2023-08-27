package org.jetbrains.plugins.scala
package codeInsight
package intention

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

package object booleans {

  def negateAndValidateExpression(infix: ScInfixExpr, text: String)
                                 (implicit project: Project, editor: Editor): Unit = {
    val start = infix.getTextRange.getStartOffset
    val diff = editor.getCaretModel.getOffset - infix.operation.nameId.getTextRange.getStartOffset

    val (anchor, replacement, size) = negateAndValidateExpressionImpl(infix, text)

    IntentionPreviewUtils.write { () =>
      anchor.replaceExpression(replacement, removeParenthesis = true)
      editor.getCaretModel.moveToOffset(start + diff + size)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }

  private def negateAndValidateExpressionImpl(infix: ScInfixExpr, text: String)
                                             (implicit context: Project): (ScExpression, ScExpression, Int) = {
    val parent = infix.getParent match {
      case p: ScParenthesisedExpr => p.getParent
      case p => p
    }

    parent match {
      case prefix: ScPrefixExpr if prefix.operation.textMatches("!") =>
        val newExpr = createExpressionFromText(text, infix)

        val size = newExpr match {
          case infix: ScInfixExpr => infix.operation.nameId.getTextRange.getStartOffset -
            newExpr.getTextRange.getStartOffset - 2
          case _ => 0
        }

        (parent.asInstanceOf[ScPrefixExpr], newExpr, size)
      case _ =>
        val newExpr = createExpressionFromText("!(" + text + ")", infix)

        val children = newExpr.asInstanceOf[ScPrefixExpr].getLastChild.asInstanceOf[ScParenthesisedExpr].getChildren
        val size = children(0) match {
          case infix: ScInfixExpr => infix.operation.
            nameId.getTextRange.getStartOffset - newExpr.getTextRange.getStartOffset
          case _ => 0
        }
        (infix, newExpr, size)
    }
  }

}
