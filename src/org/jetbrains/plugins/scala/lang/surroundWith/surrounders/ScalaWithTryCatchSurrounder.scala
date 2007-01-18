package org.jetbrains.plugins.scala.lang.surroundWith.surrounders;

/**
 * @author: Dmitry Krasilschikov
 */

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._

class ScalaWithTryCatchSurrounder extends ScalaWithTrySurrounder {
    override def isApplicable(expr : ScExprImpl) : Boolean = {
    expr.isInstanceOf[ScBlockImpl]
  }

  override def getExpressionTemplateAsString (exprAsString : String) = "try {" + exprAsString + "}" + "catch { \n }"

  override def getTemplateDescription = "try / catch"

  override def getSurroundSelectionRange (tryNode : ASTNode ) : TextRange = {
    val offset = tryNode.getTextRange.getEndOffset
    new TextRange(offset, offset)
  }
}