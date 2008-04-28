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
import org.jetbrains.plugins.scala.lang.psi.api.expr._

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
    val newNode: ASTNode = ScalaPsiElementFactory.createExpressionFromText(getTemplateAsString(elements),
      elements(0).getManager)
    var childNode: ASTNode = null

    for (child <- elements) {
      if (childNode == null) {
        childNode = child.getNode
        childNode.getTreeParent.replaceChild(childNode,newNode)
      }
      else {
        childNode = child.getNode
        childNode.getTreeParent.removeChild(childNode)
      }
    }
    return getSurroundSelectionRange(newNode);
  }

  def isApplicable(expr : PsiElement) : Boolean

  def getExpressionTemplateAsString (exprString : ASTNode) : String
  def getTemplateAsString(elements: Array[PsiElement]): String = {
    var s: String = ""
    for (element <- elements) {
      s = s + element.getNode.getText
    }
    return s
  }

  def getSurroundSelectionRange (node : ASTNode) : TextRange
}
