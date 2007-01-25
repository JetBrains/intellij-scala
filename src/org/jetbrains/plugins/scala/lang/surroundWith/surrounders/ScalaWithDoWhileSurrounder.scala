package org.jetbrains.plugins.scala.lang.surroundWith.surrounders

/**
 * @author: Dmitry Krasilschikov
 */

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange

import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

/*
 * ScalaWithDoWhileSurrounder sourrounds expression by do - while construction
*/

class ScalaWithDoWhileSurrounder extends ScalaExpressionSurrounder {

  override def isApplicable(expr : ScExprImpl) : Boolean = {
//  {if (expr.getNextSibling != null) inBraces = ScalaTokenTypes.tDOT.equals(expr.getNextSibling.getNode.getElementType);
//     inBraces}
    true
  }

  override def getExpressionTemplateAsString (expr : ASTNode) = {
    val exprAsString = "do { \n " + expr.getText + "\n" + "} while (true)"

    if (!isNeedBraces(expr)) exprAsString
    else "(" + exprAsString + ")"
  }

  override def getTemplateDescription = "do / while"

  override def getSurroundSelectionRange (withDoWhileNode : ASTNode ) : TextRange = {
    def isDoWhileStmt = (e : PsiElement) => e.isInstanceOf[ScDoStmtImpl]

    val doWhileStmt = if (isNeedBraces(withDoWhileNode)) withDoWhileNode.getPsi.asInstanceOf[ScalaPsiElementImpl].
                        childSatisfyPredicateForPsiElement(isDoWhileStmt).asInstanceOf[ScDoStmtImpl]
                      else withDoWhileNode.getPsi.asInstanceOf[ScDoStmtImpl]

    val conditionNode : ASTNode = doWhileStmt.condition.getNode

    val startOffset = conditionNode.getTextRange.getStartOffset
    val endOffset = conditionNode.getTextRange.getEndOffset

    return new TextRange(startOffset, endOffset);
  }
}