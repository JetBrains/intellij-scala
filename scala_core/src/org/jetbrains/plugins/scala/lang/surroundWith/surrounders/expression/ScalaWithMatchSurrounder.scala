package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

/**
* @author Alexander.Podkhalyuz
* Date: 28.04.2008
*/

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange


import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import lang.psi.api.expr._

class ScalaWithMatchSurrounder extends ScalaExpressionSurrounder {
  override def isApplicable(elements : Array[PsiElement]) : Boolean = {
    if (elements.length > 1) return false
    for (val element <- elements)
      if (!isApplicable(element)) return false
    return true
  }
  override def getExpressionTemplateAsString (expr : ASTNode) = {
    val exprAsString = "while (true) { \n " + expr.getText + "\n" + "}"

    if (!isNeedBraces(expr)) exprAsString
    else "(" + exprAsString + ")"
  }

  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    return super.getTemplateAsString(elements) + " match {\ncase a  => {  }\ncase _ =>\n}"
  }

  override def getTemplateDescription = "match"

  override def getSurroundSelectionRange (withMatchNode : ASTNode ) : TextRange = {
    val whileStmt = withMatchNode.getPsi.asInstanceOf[ScMatchStmt]

    val patternNode : ASTNode = whileStmt.getNode.getLastChildNode.getTreePrev.getTreePrev.getFirstChildNode.getFirstChildNode.getTreeNext.getTreeNext
    val offset = patternNode.getTextRange.getStartOffset
    patternNode.getTreeParent.removeChild(patternNode)

    return new TextRange(offset, offset);
  }
}