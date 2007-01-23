 package org.jetbrains.plugins.scala.lang.surroundWith.surrounders;

 /**
  * @author: Dmitry Krasilschikov
  */

 import org.jetbrains.plugins.scala.lang.psi.impl.expressions.ScExprImpl
 import com.intellij.lang.ASTNode
 import com.intellij.openapi.util.TextRange

/*
 * ScalaWithBracketsSurrounder is responsible of surrounders, witch enclause expression in brackets
*/

 class ScalaWithBracketsSurrounder (lBracket : String, rBracket : String) extends ScalaExpressionSurrounder {
   override def isApplicable(expr : ScExprImpl) : Boolean = true

   override def getExpressionTemplateAsString (exprAsString : String) = lBracket + exprAsString + rBracket

   override def getTemplateDescription = lBracket + "expression" + rBracket

   override def getSurroundSelectionRange (expr : ASTNode) : TextRange = {
     val offset = expr.getTextRange.getEndOffset
     new TextRange(offset, offset)
   }
 }