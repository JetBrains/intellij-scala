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
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

abstract class ScalaSurrounderByExpression extends Surrounder {

  override def isApplicable(elements : Array[PsiElement]) : Boolean = {
    var isAppl = true
    for (val element <- elements)
      isAppl = isAppl && isApplicable(element)
    isAppl  
  }

  def isNeedBraces(expr : ASTNode) = {
    if (expr.getTreeNext != null) ScalaTokenTypes.tDOT.equals(expr.getTreeNext.getElementType)
    else false
  }

  override def surroundElements(project : Project, editor : Editor, elements : Array[PsiElement]) : TextRange = {
      var newNode : ASTNode = null
      var childNode : ASTNode = null

      for (val child <- elements) {
        childNode = child.getNode
        newNode = Expr.createExpressionFromText(getExpressionTemplateAsString(childNode), child.getManager)

        childNode.getTreeParent.replaceChild(childNode, newNode)
      }

      return getSurroundSelectionRange(newNode);
  }

  def isApplicable(expr : PsiElement) : Boolean

  def getExpressionTemplateAsString (exprString : ASTNode) : String

  def getSurroundSelectionRange (node : ASTNode) : TextRange
}
