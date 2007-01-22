package org.jetbrains.plugins.scala.lang.surroundWith.surrounders

/**
 * @author: Dmitry Krasilschikov
 */

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._


class ScalaWithWhileSurrounder extends ScalaExpressionSurrounder {
  override def isApplicable(expr : ScExprImpl) : Boolean = {
//    expr.isInstanceOf[ScExprImpl]
   true
  }

  override def getExpressionTemplateAsString (exprAsString : String) = "while (true) { \n " + exprAsString + "\n" + "}"

  override def getTemplateDescription = "while"

  override def getSurroundSelectionRange (whileNode : ASTNode ) : TextRange = {
    val whileStmt = whileNode.getPsi.asInstanceOf[ScWhileStmtImpl]
    val conditionNode : ASTNode = whileStmt.condition.getNode

    val startOffset = conditionNode.getTextRange.getStartOffset
    val endOffset = conditionNode.getTextRange.getEndOffset

//    whileStmt.getNode.removeChild(conditionNode)

    return new TextRange(startOffset, endOffset);
  }
}