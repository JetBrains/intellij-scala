package org.jetbrains.plugins.scala.lang.surroundWith.surrounders;

/**
 * @author: Dmitry Krasilschikov
 */

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._

class ScalaWithForSurrounder extends ScalaExpressionSurrounder {
    override def isApplicable(expr : ScExprImpl) : Boolean = {
    //todo: condition must be ScExpr1Impl
    expr.isInstanceOf[ScExprImpl]
  }

  override def getExpressionTemplateAsString (exprAsString : String) = "for (a) " + exprAsString

  override def getTemplateDescription = "for"

  override def getSurroundSelectionRange (forNode : ASTNode ) : TextRange = {
    val enums = forNode.getPsi.asInstanceOf[ScForStmtImpl].enumerators.getNode

    val offset = enums.getTextRange.getStartOffset
    forNode.removeChild(enums)

    new TextRange(offset, offset);
  }
}




