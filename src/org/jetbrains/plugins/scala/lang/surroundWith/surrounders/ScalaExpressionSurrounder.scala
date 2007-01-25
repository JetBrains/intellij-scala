package org.jetbrains.plugins.scala.lang.surroundWith.surrounders;

/**
 * User: Dmitry.Krasilschikov
 * Date: 09.01.2007
 * Time: 14:39:44
 */

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.util.DebugPrint
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr

import org.jetbrains.plugins.scala.lang.psi.impl.expressions.ScExprImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

abstract class ScalaExpressionSurrounder extends Surrounder {

  override def isApplicable(elements : Array[PsiElement]) : Boolean = {
    val expr = elements(0).asInstanceOf[ScExprImpl]
    isApplicable(expr)
  }

  def isNeedBraces(expr : ASTNode) = {
    if (expr.getTreeNext != null) ScalaTokenTypes.tDOT.equals(expr.getTreeNext.getElementType)
    else false
  }

  override def surroundElements(project : Project, editor : Editor, elements : Array[PsiElement]) : TextRange = {
//    surroundExpression(project, editor, elements(0).asInstanceOf[ScExprImpl])

      var newNode : ASTNode = null
      var childNode : ASTNode = null

      for (val child <- elements) {
        childNode = child.getNode
        newNode = Expr.createExpressionFromText(getExpressionTemplateAsString(childNode), child.getManager)

        childNode.getTreeParent.replaceChild(childNode, newNode)
      }

      return getSurroundSelectionRange(newNode);
  }

  def isApplicable(expr : ScExprImpl) : Boolean

//  def getExpressionTemplateAsString (exprString : String) : String

  def getExpressionTemplateAsString (exprString : ASTNode) : String

  def getSurroundSelectionRange (node : ASTNode) : TextRange
}
