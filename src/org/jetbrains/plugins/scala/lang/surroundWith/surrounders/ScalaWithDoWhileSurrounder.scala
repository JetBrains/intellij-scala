package org.jetbrains.plugins.scala.lang.surroundWith.surrounders

/**
 * @author: Dmitry Krasilschikov
 */

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._

/*
 * ScalaWithDoWhileSurrounder sourrounds expression by do - while construction
*/

class ScalaWithDoWhileSurrounder extends ScalaExpressionSurrounder {
  override def isApplicable(expr : ScExprImpl) : Boolean = {
    true
  }

  override def getExpressionTemplateAsString (exprAsString : String) = "do { \n " + exprAsString + "\n" + "} while (true)"

  override def getTemplateDescription = "do / while"

  override def getSurroundSelectionRange (doWhileNode : ASTNode ) : TextRange = {
    val doWhileStmt = doWhileNode.getPsi.asInstanceOf[ScDoStmtImpl]
    val conditionNode : ASTNode = doWhileStmt.condition.getNode

    val startOffset = conditionNode.getTextRange.getStartOffset
    val endOffset = conditionNode.getTextRange.getEndOffset

    return new TextRange(startOffset, endOffset);
  }
}