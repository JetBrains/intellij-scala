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
    expr.isInstanceOf[ScExprImpl]
  }

  override def getExpressionTemplateAsString (exprAsString : String) = "for (val a <- as) yield " + exprAsString

  override def getTemplateDescription = "for / yield"

  override def getSurroundSelectionRange (forNode : ASTNode ) : TextRange = {
    val enums = forNode.getPsi.asInstanceOf[ScForStmtImpl].enumerators.getNode

    val offset = enums.getTextRange.getStartOffset
    forNode.removeChild(enums)

    new TextRange(offset, offset);
  }
}




