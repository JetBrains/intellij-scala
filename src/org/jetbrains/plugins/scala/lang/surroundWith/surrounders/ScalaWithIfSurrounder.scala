package org.jetbrains.plugins.scala.lang.surroundWith.surrounders;

/**
 * @author: Dmitry Krasilschikov
 */

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._

class ScalaWithIfSurrounder extends ScalaExpressionSurrounder {
    override def isApplicable(expr : ScExprImpl) : Boolean = {
    //todo: condition must be ScExpr1Impl
    expr.isInstanceOf[ScExpr1Impl]
  }

  override def getExpressionTemplateAsString (exprAsString : String) = "if (a) " + exprAsString

  override def getTemplateDescription = "if (condition) expression"

  override def getSurroundSelectionRange (ifNode : ASTNode ) : TextRange = {
    val stmt : ScIfStmtImpl = ifNode.getPsi.asInstanceOf[ScIfStmtImpl]
    val conditionNode : ASTNode = stmt.condition.getNode
    val offset = conditionNode.getStartOffset();
    stmt.getNode.removeChild(conditionNode)

    return new TextRange(offset, offset);
  }
}




