package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

/**
 * @author: Dmitry Krasilschikov
 */

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange


import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr._

/*
 * Surrounds expression with { } and if: if { <Cursor> } { Expression }
 */

class ScalaWithIfElseSurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    return "if (a) { " + super.getTemplateAsString(elements) + "} else {  }"
  }

  override def getTemplateDescription = "if / else"

  override def getSurroundSelectionRange(nodeWithIfNode : ASTNode ) : TextRange = {
    def isIfStmt = (e : PsiElement) => e.isInstanceOf[ScIfStmtImpl]

    val stmt = nodeWithIfNode.getPsi.asInstanceOf[ScIfStmtImpl]

    val conditionNode : ASTNode = (stmt.condition: @unchecked) match {
        case Some(c) => c.getNode
    }

    val offset = conditionNode.getStartOffset();
    stmt.getNode.removeChild(conditionNode)

    return new TextRange(offset, offset);
  }
}




