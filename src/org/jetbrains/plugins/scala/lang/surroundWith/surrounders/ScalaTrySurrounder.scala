package org.jetbrains.plugins.scala.lang.surroundWith.surrounders;

/**
 * @author: Dmitry Krasilschikov
 */

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._

class ScalaWithTrySurrounder extends ScalaExpressionSurrounder {
    override def isApplicable(expr : ScExprImpl) : Boolean = {
    expr.isInstanceOf[ScBlockImpl]
//todo: add node BLOCK in Psi
//      true
  
  }

  override def getExpressionTemplateAsString (expr : ASTNode) = "try {" + expr.getText + "}"

  override def getTemplateDescription = "try"

  override def getSurroundSelectionRange (tryNode : ASTNode ) : TextRange = {
    val offset = tryNode.getTextRange.getEndOffset
    new TextRange(offset, offset)
  }
}