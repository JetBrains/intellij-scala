package org.jetbrains.plugins.scala
package codeInsight
package intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.project.ProjectContext

package object booleans {

  def negateAndValidateExpression(infix: ScInfixExpr, text: String)
                                 (implicit project: Project, editor: Editor): Unit = {
    val start = infix.getTextRange.getStartOffset
    val diff = editor.getCaretModel.getOffset - infix.operation.nameId.getTextRange.getStartOffset

    val (anchor, replacement, size) = negateAndValidateExpressionImpl(infix, text)

    inWriteAction {
      anchor.replaceExpression(replacement, removeParenthesis = true)
      editor.getCaretModel.moveToOffset(start + diff + size)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }

  private def negateAndValidateExpressionImpl(infix: ScInfixExpr, text: String)
                                             (implicit context: ProjectContext): (ScExpression, ScExpression, Int) = {
    val parent = infix.getParent match {
      case p: ScParenthesisedExpr => p.getParent
      case p => p
    }

    parent match {
      case prefix: ScPrefixExpr if prefix.operation.getText == "!" =>
        val newExpr = createExpressionFromText(text)

        val size = newExpr match {
          case infix: ScInfixExpr => infix.operation.nameId.getTextRange.getStartOffset -
            newExpr.getTextRange.getStartOffset - 2
          case _ => 0
        }

        (parent.asInstanceOf[ScPrefixExpr], newExpr, size)
      case _ =>
        val newExpr = createExpressionFromText("!(" + text + ")")

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
