package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import psi.api.expr.{ScIfStmt, ScExpression}

/**
 * User: Alexander Podkhalyuzin
 * Date: 29.09.2008
 */

class ScalaWithIfElseConditionSurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "if (" + super.getTemplateAsString(elements) + ") {}\nelse {}"
  override def getExpressionTemplateAsString(exprString: ASTNode): String = "if (" + exprString.getText + ") {}\nelse {}"
  override def getTemplateDescription: String = "if (expr) {...} else {...}"
  override def isApplicable(elements: Array[PsiElement]): Boolean = {
    if (elements.length != 1) return false
    elements(0) match {
      case x: ScExpression if x.getType == psi.types.Boolean => return true
      case _ => return false
    }
  }
  override def getSurroundSelectionRange(withIfNode: ASTNode): TextRange = {
    val ifStmt: ScIfStmt = withIfNode.getPsi.asInstanceOf[ScIfStmt]
    val body = (ifStmt.thenBranch: @unchecked) match {
      case Some(x) => x
    }
    val offset = body.getTextRange.getStartOffset + 1
    new TextRange(offset,offset)
  }
}