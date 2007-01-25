package org.jetbrains.plugins.scala.lang.surroundWith.surrounders;

/**
 * @author: Dmitry Krasilschikov
 */

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange

import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

class ScalaWithIfSurrounder extends ScalaExpressionSurrounder {
//  private var inBraces : Boolean = false

  override def isApplicable(expr : ScExprImpl) : Boolean = {
    expr.isInstanceOf[ScExpr1Impl] 
  }

  override def getExpressionTemplateAsString (expr : ASTNode) =
    if (!isNeedBraces(expr)) "if (a) " + expr.getText
    else "(" + "if (a) " + expr.getText + ")"

  override def getTemplateDescription = "if (condition) expression"

  override def getSurroundSelectionRange (nodeWithIfNode : ASTNode ) : TextRange = {
    def isIfStmt = (e : PsiElement) => e.isInstanceOf[ScIfStmtImpl]

    val stmt = if (isNeedBraces(nodeWithIfNode)) nodeWithIfNode.getPsi.asInstanceOf[ScalaPsiElementImpl].childSatisfyPredicateForPsiElement(isIfStmt).asInstanceOf[ScIfStmtImpl]
                else nodeWithIfNode.getPsi.asInstanceOf[ScIfStmtImpl]

    val conditionNode : ASTNode = stmt.condition.getNode
    val offset = conditionNode.getStartOffset();
    stmt.getNode.removeChild(conditionNode)

    return new TextRange(offset, offset);
  }
}




