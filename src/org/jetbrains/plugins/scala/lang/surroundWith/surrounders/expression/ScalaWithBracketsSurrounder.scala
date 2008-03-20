 package org.jetbrains.plugins.scala.lang.surroundWith.surrounders;

 /**
  * @author: Dmitry Krasilschikov
  */


 import com.intellij.lang.ASTNode
 import com.intellij.openapi.util.TextRange

/*
 * ScalaWithBracketsSurrounder is responsible of surrounders, witch enclause expression in brackets: { Expression } or ( Expression )
*/

 class ScalaWithBracketsSurrounder (lBracket : String, rBracket : String) extends ScalaExpressionSurrounder {

   override def getExpressionTemplateAsString (expr : ASTNode) = lBracket + expr.getText + rBracket

   override def getTemplateDescription = lBracket + "expression" + rBracket

   override def getSurroundSelectionRange (expr : ASTNode) : TextRange = {
     val offset = expr.getTextRange.getEndOffset
     new TextRange(offset, offset)
   }
 }