package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

/**
 * User: Dmitry.Krasilschikov
 * Date: 09.01.2007
 *
 */

import com.intellij.lang.surroundWith.Surrounder
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import psi.impl.ScalaPsiElementFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.diagnostic.Logger;

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.util.DebugPrint
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr



import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import lang.psi.api.statements._

/*
 * Surrounds an expression and return an expression
 */

abstract class ScalaExpressionSurrounder extends Surrounder {
  def isApplicable(element : PsiElement) : Boolean = {
    element match {
      case _ : ScExpression | _: PsiWhiteSpace | _: ScValue | _: ScVariable  => {
        true
      }
      case e => {
        if (e.getNode.getElementType == ScalaTokenTypes.tLINE_TERMINATOR) true
        else if (e.getNode.getElementType == ScalaTokenTypes.tSEMICOLON) true
        else if (ScalaTokenTypes.COMMENTS_TOKEN_SET contains e.getNode.getElementType) true
        else false
      }
    }
  }

  def needParenthesis(elements: Array[PsiElement]): Boolean = {
    if (elements.length > 1) return false
    val element = elements(0)
    val parent = element.getParent
    parent match {
      case _: ScInfixExpr => true
      case _ => false
    }
  }

  override def isApplicable(elements : Array[PsiElement]) : Boolean = {
    for (val element <- elements)
      if (!isApplicable(element)) return false
    true
  }

  override def surroundElements(project : Project, editor : Editor, elements : Array[PsiElement]) : TextRange = {
    val newNode = ScalaPsiElementFactory.createExpressionFromText(
      if (needParenthesis(elements)) "(" + getTemplateAsString(elements) + ")"
      else getTemplateAsString(elements),
      elements(0).getManager
    ).getNode
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

  def getTemplateAsString(elements: Array[PsiElement]): String = {
    var s: String = ""
    for (element <- elements) {
      s = s + element.getNode.getText
    }
    return s
  }

  def getSurroundSelectionRange (node : ASTNode) : TextRange
}