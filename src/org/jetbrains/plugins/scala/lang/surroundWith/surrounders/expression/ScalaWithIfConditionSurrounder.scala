package org.jetbrains.plugins.scala
package lang
package surroundWith
package surrounders
package expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import psi.api.expr.{ScParenthesisedExpr, ScIfStmt, ScExpression}

/**
 * User: Alexander Podkhalyuzin
 * Date: 29.09.2008
 */

class ScalaWithIfConditionSurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "if (" + super.getTemplateAsString(elements) + ") {}"
  override def getTemplateDescription: String = "if (expr) {...}"
  override def isApplicable(elements: Array[PsiElement]): Boolean = {
    if (elements.length != 1) return false
    elements(0) match {
      case x: ScExpression if x.getType == psi.types.Boolean => return true
      case _ => return false
    }
  }
  override def getSurroundSelectionRange(withIfNode: ASTNode): TextRange = {
    val element: PsiElement = withIfNode.getPsi match {
      case x: ScParenthesisedExpr => x.expr match {
        case Some(y) => y
        case _ => return x.getTextRange
      }
      case x => x
    }

    val ifStmt: ScIfStmt = element.asInstanceOf[ScIfStmt]
    val body = (ifStmt.thenBranch: @unchecked) match {
      case Some(x) => x
    }
    val offset = body.getTextRange.getStartOffset + 1
    new TextRange(offset,offset)
  }
}