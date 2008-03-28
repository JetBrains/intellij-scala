package org.jetbrains.plugins.scala.lang.surroundWith.surrounders;

/**
 * User: Dmitry.Krasilschikov
 * Date: 09.01.2007
 *
 */

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.util.TextRange
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/*
 * ScalaSurrounderByExpression surround smth and return an expression
 */

abstract class ScalaSurrounderByExpression extends Surrounder {

  override def isApplicable(elements : Array[PsiElement]) : Boolean = {
    for (val element <- elements)
      if (!isApplicable(element)) return false
    true
  }

  def isNeedBraces(expr : ASTNode) = {
    if (expr.getTreeNext != null) ScalaTokenTypes.tDOT.equals(expr.getTreeNext.getElementType)
    else false
  }

  override def surroundElements(project : Project, editor : Editor, elements : Array[PsiElement]) : TextRange = {
/*
      var newNode : ASTNode = null
      var childNode : ASTNode = null

      for (val child <- elements) {
        childNode = child.getNode
        newNode = ScalaPsiElementFactory.createExpressionFromText(getExpressionTemplateAsString(childNode), child.getManager)

        childNode.getTreeParent.replaceChild(childNode, newNode)
      }

      return getSurroundSelectionRange(newNode);
*/
    return null
  }

  def isApplicable(expr : PsiElement) : Boolean

  def getExpressionTemplateAsString (exprString : ASTNode) : String

  def getSurroundSelectionRange (node : ASTNode) : TextRange
}
